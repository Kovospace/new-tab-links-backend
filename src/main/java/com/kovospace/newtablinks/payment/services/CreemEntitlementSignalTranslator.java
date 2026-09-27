package com.kovospace.newtablinks.payment.services;

import static com.kovospace.newtablinks.payment.utils.WebhookPayloadFields.identifierOf;
import static com.kovospace.newtablinks.payment.utils.WebhookPayloadFields.instantOrNull;
import static com.kovospace.newtablinks.payment.utils.WebhookPayloadFields.longOrNull;
import static com.kovospace.newtablinks.payment.utils.WebhookPayloadFields.textOrNull;

import com.kovospace.newtablinks.entitlement.models.ChargedAmount;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignal;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalKind;
import com.kovospace.newtablinks.entitlement.models.PaymentProvider;
import com.kovospace.newtablinks.entitlement.models.ProviderPurchaseReferences;
import com.kovospace.newtablinks.payment.models.CreemCheckoutMetadata;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Translates the {@code object} of a verified Creem webhook event into an
 * {@link EntitlementSignal}.
 *
 * <p>Event names and payload shapes follow https://docs.creem.io/code/webhooks as read on
 * 2026-09-27. Only the events that change what an account is entitled to are translated; every
 * other type - {@code subscription.update}, {@code subscription.trialing},
 * {@code subscription.paused}, {@code dispute.created}, the credit events - is acknowledged and
 * left alone.</p>
 *
 * @since 0.0.9
 */
@Component
public class CreemEntitlementSignalTranslator {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CreemEntitlementSignalTranslator.class);

    /** Creem's event for a completed checkout, one-time or the first payment of a subscription. */
    static final String CHECKOUT_COMPLETED = "checkout.completed";

    /** Creem's event for a refund the merchant issued. */
    static final String REFUND_CREATED = "refund.created";

    /** Creem's order type for a one-time purchase. */
    private static final String ONE_TIME_ORDER_TYPE = "onetime";

    /**
     * Subscription lifecycle events, each carrying a subscription as its object.
     *
     * <p>{@code subscription.unpaid} is treated as {@code past_due}, as Creem's documentation
     * advises: both mean a renewal failed, and both mark rather than revoke.</p>
     */
    private static final Map<String, EntitlementSignalKind> SUBSCRIPTION_EVENT_KINDS = Map.of(
            "subscription.active", EntitlementSignalKind.SUBSCRIPTION_ACTIVATED,
            "subscription.paid", EntitlementSignalKind.SUBSCRIPTION_PAID,
            "subscription.past_due", EntitlementSignalKind.SUBSCRIPTION_PAYMENT_FAILED,
            "subscription.unpaid", EntitlementSignalKind.SUBSCRIPTION_PAYMENT_FAILED,
            "subscription.scheduled_cancel",
            EntitlementSignalKind.SUBSCRIPTION_CANCELLATION_SCHEDULED,
            "subscription.canceled", EntitlementSignalKind.SUBSCRIPTION_CANCELED,
            "subscription.expired", EntitlementSignalKind.SUBSCRIPTION_EXPIRED);

    /**
     * Translates one event.
     *
     * @param eventType   Creem's {@code eventType}
     * @param occurredAt  Creem's {@code created_at} of the event
     * @param eventObject the event's {@code object}
     * @return the signal, or empty when the event does not affect an entitlement
     */
    public Optional<EntitlementSignal> translate(
            final String eventType,
            final Instant occurredAt,
            final JsonNode eventObject) {

        if (CHECKOUT_COMPLETED.equals(eventType)) {
            return Optional.of(fromCompletedCheckout(occurredAt, eventObject));
        }
        if (REFUND_CREATED.equals(eventType)) {
            return fromRefund(occurredAt, eventObject);
        }
        return Optional.ofNullable(SUBSCRIPTION_EVENT_KINDS.get(eventType))
                .map(kind -> fromSubscription(kind, occurredAt, eventObject));
    }

    /**
     * Translates a completed checkout: a lifetime purchase, or the start of a subscription.
     *
     * @param occurredAt the event's timestamp
     * @param checkout   the checkout object
     * @return the signal
     */
    private EntitlementSignal fromCompletedCheckout(final Instant occurredAt, final JsonNode checkout) {
        final JsonNode order = checkout.path("order");
        final JsonNode subscription = checkout.path("subscription");
        final boolean isOneTime = ONE_TIME_ORDER_TYPE.equals(textOrNull(order, "type"));

        final ProviderPurchaseReferences references = new ProviderPurchaseReferences(
                firstPresent(identifierOf(checkout.path("customer")), textOrNull(order, "customer")),
                isOneTime ? null : identifierOf(subscription),
                firstPresent(identifierOf(checkout.path("product")), textOrNull(order, "product")),
                textOrNull(order, "id"));

        return new EntitlementSignal(
                isOneTime
                        ? EntitlementSignalKind.LIFETIME_PURCHASED
                        : EntitlementSignalKind.SUBSCRIPTION_PURCHASED,
                PaymentProvider.CREEM,
                occurredAt,
                firstPresent(accountIdIn(checkout), accountIdIn(subscription)),
                references,
                subscription.isObject() ? instantOrNull(subscription, "current_period_end_date") : null,
                chargedAmountOf(order));
    }

    /**
     * Translates a subscription lifecycle event.
     *
     * @param kind         what the event means
     * @param occurredAt   the event's timestamp
     * @param subscription the subscription object
     * @return the signal
     */
    private EntitlementSignal fromSubscription(
            final EntitlementSignalKind kind,
            final Instant occurredAt,
            final JsonNode subscription) {

        final ProviderPurchaseReferences references = new ProviderPurchaseReferences(
                identifierOf(subscription.path("customer")),
                textOrNull(subscription, "id"),
                identifierOf(subscription.path("product")),
                null);

        return new EntitlementSignal(
                kind,
                PaymentProvider.CREEM,
                occurredAt,
                accountIdIn(subscription),
                references,
                instantOrNull(subscription, "current_period_end_date"),
                kind.confirmsSubscriptionPayment()
                        ? chargedAmountOf(subscription.path("last_transaction"))
                        : null);
    }

    /**
     * Translates a refund, when it gives back everything that was paid.
     *
     * <p>A partial refund is a goodwill gesture, not a reversal of the purchase, and leaves the
     * entitlement alone.</p>
     *
     * @param occurredAt the event's timestamp
     * @param refund     the refund object
     * @return the signal, or empty for a partial refund
     */
    private Optional<EntitlementSignal> fromRefund(final Instant occurredAt, final JsonNode refund) {
        final JsonNode transaction = refund.path("transaction");
        if (!isFullRefund(refund, transaction)) {
            LOGGER.info("A partial Creem refund left the entitlement alone");
            return Optional.empty();
        }

        final ProviderPurchaseReferences references = new ProviderPurchaseReferences(
                identifierOf(refund.path("customer")),
                firstPresent(identifierOf(refund.path("subscription")),
                        textOrNull(transaction, "subscription")),
                null,
                firstPresent(identifierOf(refund.path("order")), textOrNull(transaction, "order")));

        return Optional.of(new EntitlementSignal(
                EntitlementSignalKind.PAYMENT_REFUNDED,
                PaymentProvider.CREEM,
                occurredAt,
                firstPresent(accountIdIn(refund.path("checkout")),
                        accountIdIn(refund.path("subscription"))),
                references,
                null,
                null));
    }

    /**
     * Tells whether a refund gives back all that was paid for its transaction.
     *
     * <p>When the amounts are missing the refund is treated as full: revoking on a refund that
     * was in fact partial is an operator's easy fix, whereas keeping pro on a fully refunded
     * purchase goes unnoticed.</p>
     *
     * @param refund      the refund object
     * @param transaction the refunded transaction
     * @return {@code true} unless the amounts show a partial refund
     */
    private static boolean isFullRefund(final JsonNode refund, final JsonNode transaction) {
        final Long amountPaid = longOrNull(transaction, "amount_paid");
        final Long amountRefunded = firstPresent(
                longOrNull(transaction, "refunded_amount"), longOrNull(refund, "refund_amount"));
        return amountPaid == null || amountRefunded == null || amountRefunded >= amountPaid;
    }

    /**
     * Reads what the customer actually paid from an order or a transaction.
     *
     * <p>{@code amount_paid} is what left the customer's card, tax included; {@code amount} is the
     * fallback for payloads that do not carry it.</p>
     *
     * @param orderOrTransaction the order or transaction object, possibly missing
     * @return the amount, or {@code null} when not reported
     */
    private static ChargedAmount chargedAmountOf(final JsonNode orderOrTransaction) {
        final Long amount = firstPresent(
                longOrNull(orderOrTransaction, "amount_paid"),
                longOrNull(orderOrTransaction, "amount"));
        final String currency = textOrNull(orderOrTransaction, "currency");
        if (amount == null || currency == null) {
            return null;
        }
        try {
            return new ChargedAmount(amount, currency);
        } catch (final IllegalArgumentException unusableAmount) {
            LOGGER.warn("Ignoring a charged amount Creem reported in an unusable form: {}",
                    unusableAmount.getMessage());
            return null;
        }
    }

    /**
     * Reads the paying account from an object's metadata.
     *
     * @param objectWithMetadata a checkout, subscription or other object, possibly missing
     * @return the account identifier, or {@code null} when absent or not an identifier
     */
    private static UUID accountIdIn(final JsonNode objectWithMetadata) {
        final String value = textOrNull(
                objectWithMetadata.path("metadata"), CreemCheckoutMetadata.ACCOUNT_ID_KEY);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (final IllegalArgumentException notAnIdentifier) {
            LOGGER.warn("Creem metadata carried an account reference that is not an identifier");
            return null;
        }
    }

    /**
     * Returns the first of two values that is present.
     *
     * @param preferred the value to use when present
     * @param fallback  the value to use otherwise
     * @param <T>       the value type
     * @return {@code preferred}, or {@code fallback} when it is {@code null}
     */
    private static <T> T firstPresent(final T preferred, final T fallback) {
        return preferred != null ? preferred : fallback;
    }
}
