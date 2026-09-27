package com.kovospace.newtablinks.entitlement.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import com.kovospace.newtablinks.user.models.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What makes one account pro, and until when.
 *
 * <p>At most one per account ({@code uk_user_entitlement_user}). It is written only by payment
 * signals and, later, by the operator; it is the single answer to "is this account pro", so
 * nothing ever asks the payment provider that question on a request.</p>
 *
 * <p>The schema is {@code V10__pro_entitlement.sql} in {@code new-tab-links-migrations}. Its CHECK
 * constraints - the allowed sources and statuses, the amount and currency travelling together, a
 * grant carrying no payment - are enforced there and nowhere in this class; {@code validate} does
 * not see them.</p>
 *
 * @since 0.0.9
 */
@Entity
@Table(name = "user_entitlement")
public class EntitlementEntity extends AbstractAuditableEntity {

    /** Length of the columns holding the provider's own identifiers. */
    private static final int PROVIDER_IDENTIFIER_LENGTH = 100;

    /**
     * The account this entitlement belongs to. Always present, never changed.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserEntity owner;

    /** Where the entitlement came from. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private EntitlementSource source;

    /** Where it stands in the provider's lifecycle. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EntitlementStatus status;

    /** End of what has been paid for; {@code null} for a lifetime purchase. */
    @Column(name = "paid_until")
    private Instant paidUntil;

    /** What the customer actually paid, in minor units; {@code null} with the currency. */
    @Column(name = "charged_amount_minor_units")
    private Long chargedAmountInMinorUnits;

    /** Currency the customer was charged in; {@code null} with the amount. */
    @Column(name = "charged_currency", length = 3)
    private String chargedCurrency;

    /** Provider the payment went through; {@code null} for a grant. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", length = 20)
    private PaymentProvider paymentProvider;

    /** The provider's customer. */
    @Column(name = "provider_customer_id", length = PROVIDER_IDENTIFIER_LENGTH)
    private String providerCustomerId;

    /** The provider's subscription; {@code null} unless the source is a subscription. */
    @Column(name = "provider_subscription_id", length = PROVIDER_IDENTIFIER_LENGTH)
    private String providerSubscriptionId;

    /** The product that was bought. */
    @Column(name = "provider_product_id", length = PROVIDER_IDENTIFIER_LENGTH)
    private String providerProductId;

    /** The provider's order - what a refund of a one-time purchase names. */
    @Column(name = "provider_order_id", length = PROVIDER_IDENTIFIER_LENGTH)
    private String providerOrderId;

    /**
     * The provider's timestamp of the newest event applied to this row.
     *
     * <p>Webhooks arrive out of order, so an event older than this is refused rather than
     * allowed to roll the entitlement back.</p>
     */
    @Column(name = "last_provider_event_at")
    private Instant lastProviderEventAt;

    /**
     * A subscription a lifetime purchase replaced, which still has to be cancelled at the
     * provider; {@code null} when there is none.
     *
     * <p>Kept after the cancellation succeeded, as the record of what was cancelled.</p>
     */
    @Column(name = "superseded_subscription_id", length = PROVIDER_IDENTIFIER_LENGTH)
    private String supersededSubscriptionId;

    /**
     * When the provider confirmed {@link #supersededSubscriptionId} cancelled; {@code null} while
     * the cancellation is still pending.
     */
    @Column(name = "superseded_subscription_cancelled_at")
    private Instant supersededSubscriptionCancelledAt;

    /**
     * Required by JPA.
     */
    protected EntitlementEntity() {
    }

    /**
     * Starts an entitlement for an account, before any purchase has been recorded on it.
     *
     * <p>The caller records a purchase straight away; the source and status set here are only
     * placeholders that keep the row valid until it does.</p>
     *
     * @param owner the account the entitlement belongs to
     */
    public EntitlementEntity(final UserEntity owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.source = EntitlementSource.SUBSCRIPTION;
        this.status = EntitlementStatus.ACTIVE;
    }

    /**
     * Makes this a lifetime entitlement bought through the given purchase.
     *
     * <p>Replaces whatever the row rested on before - a subscription, a grant or an earlier
     * refunded purchase - because a lifetime purchase outranks all of them.</p>
     *
     * @param provider      provider the payment went through
     * @param references    the provider's identifiers for the purchase
     * @param chargedAmount what the customer paid, or {@code null} when not reported
     */
    public void recordLifetimePurchase(
            final PaymentProvider provider,
            final ProviderPurchaseReferences references,
            final ChargedAmount chargedAmount) {

        this.source = EntitlementSource.LIFETIME;
        this.status = EntitlementStatus.ACTIVE;
        this.paidUntil = null;
        this.paymentProvider = provider;
        this.providerSubscriptionId = null;
        this.providerOrderId = references.orderId();
        adoptCustomerAndProduct(references);
        replaceChargedAmountWhenReported(chargedAmount);
    }

    /**
     * Remembers that the subscription this row rests on must be cancelled at the provider,
     * because a lifetime purchase is about to replace it.
     *
     * <p>Must be called <em>before</em> {@link #recordLifetimePurchase}, which clears the
     * subscription identifier. Does nothing when the row rests on no subscription. A still
     * pending cancellation of another subscription is overwritten - the caller logs that.</p>
     *
     * @return the identifier of a different subscription whose pending cancellation was dropped
     *         to make room, or {@code null} when none was
     */
    public String scheduleCancellationOfCurrentSubscription() {
        if (source != EntitlementSource.SUBSCRIPTION || providerSubscriptionId == null) {
            return null;
        }
        final String droppedPendingSubscriptionId =
                pendingSupersededSubscriptionId()
                        .filter(pending -> !pending.equals(providerSubscriptionId))
                        .orElse(null);
        this.supersededSubscriptionId = providerSubscriptionId;
        this.supersededSubscriptionCancelledAt = null;
        return droppedPendingSubscriptionId;
    }

    /**
     * Returns the superseded subscription still waiting to be cancelled at the provider.
     *
     * @return its identifier, or empty when nothing is pending
     */
    public Optional<String> pendingSupersededSubscriptionId() {
        return supersededSubscriptionCancelledAt == null
                ? Optional.ofNullable(supersededSubscriptionId)
                : Optional.empty();
    }

    /**
     * Makes this entitlement rest on the given subscription, taking over its identifiers.
     *
     * <p>Identifiers the signal does not carry are kept rather than cleared, because different
     * provider events report different subsets of them.</p>
     *
     * @param provider   provider the subscription runs through
     * @param references the provider's identifiers for the subscription
     */
    public void attachToSubscription(
            final PaymentProvider provider,
            final ProviderPurchaseReferences references) {

        if (references.subscriptionId() != null
                && !references.subscriptionId().equals(providerSubscriptionId)) {
            this.providerOrderId = null;
        }
        this.source = EntitlementSource.SUBSCRIPTION;
        this.paymentProvider = provider;
        if (references.subscriptionId() != null) {
            this.providerSubscriptionId = references.subscriptionId();
        }
        if (references.orderId() != null) {
            this.providerOrderId = references.orderId();
        }
        adoptCustomerAndProduct(references);
    }

    /**
     * Moves the entitlement to another lifecycle status.
     *
     * @param newStatus the status the provider reported
     */
    public void changeStatus(final EntitlementStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "newStatus");
    }

    /**
     * Sets the end of the paid period, when the provider reported one.
     *
     * @param reportedPaidUntil end of the period paid for, or {@code null} to keep the current one
     */
    public void replacePaidUntilWhenReported(final Instant reportedPaidUntil) {
        if (reportedPaidUntil != null) {
            this.paidUntil = reportedPaidUntil;
        }
    }

    /**
     * Records what the customer was charged, when the provider reported it.
     *
     * @param chargedAmount the amount and currency, or {@code null} to keep what is stored
     */
    public void replaceChargedAmountWhenReported(final ChargedAmount chargedAmount) {
        if (chargedAmount != null) {
            this.chargedAmountInMinorUnits = chargedAmount.amountInMinorUnits();
            this.chargedCurrency = chargedAmount.currencyCode();
        }
    }

    /**
     * Remembers the provider timestamp of the event just applied, never moving it backwards.
     *
     * <p>A lifetime purchase is applied even when it is older than the newest event on the row;
     * the guard against out-of-order subscription events must keep judging by that newest one.</p>
     *
     * @param providerEventOccurredAt the event's own timestamp
     */
    public void markProviderEventApplied(final Instant providerEventOccurredAt) {
        if (lastProviderEventAt == null || providerEventOccurredAt.isAfter(lastProviderEventAt)) {
            this.lastProviderEventAt = providerEventOccurredAt;
        }
    }

    /**
     * Tells whether an event from the given moment is older than one already applied.
     *
     * <p>An event from the very same millisecond is not stale: Creem stamps the checkout and the
     * subscription events of one purchase identically, and both have to be applied.</p>
     *
     * @param providerEventOccurredAt the event's own timestamp
     * @return {@code true} when a newer event has already been applied
     */
    public boolean isNewerThan(final Instant providerEventOccurredAt) {
        return lastProviderEventAt != null && lastProviderEventAt.isAfter(providerEventOccurredAt);
    }

    /**
     * Tells whether this entitlement currently makes the account pro.
     *
     * @param moment the instant to judge at, normally now
     * @return {@code true} while the status honours the paid period and that period has not ended
     */
    public boolean grantsProAt(final Instant moment) {
        if (!status.honoursPaidPeriod()) {
            return false;
        }
        if (paidUntil == null) {
            // A subscription whose period end has not been reported yet grants nothing: the
            // event that carries it follows within seconds, and an open end would mean forever.
            return source != EntitlementSource.SUBSCRIPTION;
        }
        return paidUntil.isAfter(moment);
    }

    /**
     * Tells whether this is a lifetime purchase that still stands.
     *
     * @return {@code true} for a lifetime entitlement that has not been refunded
     */
    public boolean isStandingLifetimePurchase() {
        return source == EntitlementSource.LIFETIME && status != EntitlementStatus.REFUNDED;
    }

    /**
     * Tells whether this entitlement rests on the given subscription.
     *
     * @param subscriptionId the provider's subscription identifier, possibly {@code null}
     * @return {@code true} when the row is a subscription entitlement for exactly that subscription
     */
    public boolean restsOnSubscription(final String subscriptionId) {
        return source == EntitlementSource.SUBSCRIPTION
                && subscriptionId != null
                && subscriptionId.equals(providerSubscriptionId);
    }

    /**
     * Tells whether the given purchase is the one this entitlement rests on.
     *
     * @param references the provider's identifiers for a purchase
     * @return {@code true} when the order or the subscription matches
     */
    public boolean restsOnPurchase(final ProviderPurchaseReferences references) {
        final boolean sameOrder = references.orderId() != null
                && references.orderId().equals(providerOrderId);
        return sameOrder || restsOnSubscription(references.subscriptionId());
    }

    /**
     * Copies the customer and product identifiers the signal carries, keeping the rest.
     *
     * @param references the provider's identifiers for the purchase
     */
    private void adoptCustomerAndProduct(final ProviderPurchaseReferences references) {
        if (references.customerId() != null) {
            this.providerCustomerId = references.customerId();
        }
        if (references.productId() != null) {
            this.providerProductId = references.productId();
        }
    }

    /** @return the account this entitlement belongs to */
    public UserEntity getOwner() {
        return owner;
    }

    /** @return where the entitlement came from */
    public EntitlementSource getSource() {
        return source;
    }

    /** @return where it stands in the provider's lifecycle */
    public EntitlementStatus getStatus() {
        return status;
    }

    /** @return end of the paid period, or {@code null} when it has no end */
    public Instant getPaidUntil() {
        return paidUntil;
    }

    /** @return the amount charged in minor units, or {@code null} when unknown */
    public Long getChargedAmountInMinorUnits() {
        return chargedAmountInMinorUnits;
    }

    /** @return the currency charged in, or {@code null} when unknown */
    public String getChargedCurrency() {
        return chargedCurrency;
    }

    /** @return the provider the payment went through, or {@code null} for a grant */
    public PaymentProvider getPaymentProvider() {
        return paymentProvider;
    }

    /** @return the provider's customer identifier, or {@code null} */
    public String getProviderCustomerId() {
        return providerCustomerId;
    }

    /** @return the provider's subscription identifier, or {@code null} */
    public String getProviderSubscriptionId() {
        return providerSubscriptionId;
    }

    /** @return the provider's product identifier, or {@code null} */
    public String getProviderProductId() {
        return providerProductId;
    }

    /** @return the provider's order identifier, or {@code null} */
    public String getProviderOrderId() {
        return providerOrderId;
    }

    /** @return the provider timestamp of the newest applied event, or {@code null} */
    public Instant getLastProviderEventAt() {
        return lastProviderEventAt;
    }

    /** @return the subscription a lifetime purchase replaced, or {@code null} */
    public String getSupersededSubscriptionId() {
        return supersededSubscriptionId;
    }

    /** @return when that subscription was confirmed cancelled, or {@code null} while pending */
    public Instant getSupersededSubscriptionCancelledAt() {
        return supersededSubscriptionCancelledAt;
    }
}
