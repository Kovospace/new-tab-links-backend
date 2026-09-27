package com.kovospace.newtablinks.entitlement.services;

import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignal;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalKind;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalOutcome;
import com.kovospace.newtablinks.entitlement.models.EntitlementStatus;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decides what one payment signal does to one entitlement, and does it.
 *
 * <p>Pure: no persistence, no clock, no provider. The rules, in the order they are checked:</p>
 * <ol>
 *   <li><strong>Older than what was applied last - refused, except a lifetime purchase.</strong>
 *       Webhooks arrive out of order; a {@code paid} can land after the {@code past_due} that
 *       logically followed it, and applying it would roll the row back. A lifetime purchase is
 *       not a state in that sequence: once paid it outranks every subscription event, so it is
 *       applied whatever its timestamp - unless the row already holds that very order, as the
 *       same purchase or as its refund, and a refund always wins over its own purchase.</li>
 *   <li><strong>About another purchase - left alone.</strong> A standing lifetime purchase
 *       outranks every subscription signal. A status change for a subscription the row does not
 *       rest on is ignored. A refund counts only for the order or subscription the row rests
 *       on.</li>
 *   <li><strong>A confirmed payment takes over.</strong> A lifetime purchase, or money arriving
 *       for a subscription, replaces whatever the row rested on - a grant, an ended subscription,
 *       an earlier refunded purchase.</li>
 *   <li><strong>A failure marks, never revokes.</strong> Past due, cancelled and expired change
 *       the status and leave the paid-until instant alone, so the customer keeps what they
 *       already paid for.</li>
 * </ol>
 *
 * <p><strong>A lifetime purchase leaves a chore behind.</strong> The subscription it replaces is
 * still live at the provider and would charge again at renewal, so its identifier is kept on the
 * row as a pending cancellation before the lifetime purchase overwrites it. The cancellation
 * itself happens after the transaction commits, outside this class - see
 * {@code SupersededSubscriptionCancellationService} in the payment module. Once the row is a
 * standing lifetime purchase, the rule above makes the {@code canceled} and {@code expired}
 * events that cancellation triggers {@link EntitlementSignalOutcome#IGNORED_UNRELATED}.</p>
 *
 * @since 0.0.9
 */
@Component
public class EntitlementTransitionPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntitlementTransitionPolicy.class);

    /**
     * Applies a signal to an entitlement, unless the rules above refuse it.
     *
     * @param entitlement     the entitlement to change, already locked by the caller
     * @param freshlyCreated  whether the row was created for this very signal, in which case it
     *                        has no history for the signal to be stale against or unrelated to
     * @param signal          what the provider reported
     * @return what happened
     */
    public EntitlementSignalOutcome apply(
            final EntitlementEntity entitlement,
            final boolean freshlyCreated,
            final EntitlementSignal signal) {

        if (!freshlyCreated) {
            final Optional<EntitlementSignalOutcome> refusal = refusalOf(entitlement, signal);
            if (refusal.isPresent()) {
                return refusal.get();
            }
        }

        switch (signal.kind()) {
            case LIFETIME_PURCHASED -> replaceWithLifetimePurchase(entitlement, signal);
            case PAYMENT_REFUNDED -> entitlement.changeStatus(signal.kind().resultingStatus());
            default -> applySubscriptionSignal(entitlement, freshlyCreated, signal);
        }
        entitlement.markProviderEventApplied(signal.occurredAt());
        return EntitlementSignalOutcome.APPLIED;
    }

    /**
     * Decides whether a signal must be refused by an entitlement that already exists.
     *
     * @param entitlement the existing entitlement
     * @param signal      what the provider reported
     * @return the refusal, or empty when the signal is to be applied
     */
    private Optional<EntitlementSignalOutcome> refusalOf(
            final EntitlementEntity entitlement,
            final EntitlementSignal signal) {

        if (signal.kind() == EntitlementSignalKind.LIFETIME_PURCHASED) {
            return refusalOfLifetimePurchase(entitlement, signal);
        }
        if (entitlement.isNewerThan(signal.occurredAt())) {
            return Optional.of(EntitlementSignalOutcome.IGNORED_STALE);
        }
        if (!concernsThisEntitlement(entitlement, signal)) {
            return Optional.of(EntitlementSignalOutcome.IGNORED_UNRELATED);
        }
        return Optional.empty();
    }

    /**
     * Decides whether a lifetime purchase must be refused, which happens only when the row
     * already holds that very order.
     *
     * <p>A lifetime purchase of any other order is applied however old its timestamp: a paid
     * lifetime purchase must never be lost to delivery order. The same order is refused when it
     * has been refunded - the refund wins over its own purchase, whichever arrives last - and
     * when it is an older copy of what is already applied.</p>
     *
     * @param entitlement the existing entitlement
     * @param signal      the lifetime purchase
     * @return {@link EntitlementSignalOutcome#IGNORED_STALE}, or empty to apply it
     */
    private Optional<EntitlementSignalOutcome> refusalOfLifetimePurchase(
            final EntitlementEntity entitlement,
            final EntitlementSignal signal) {

        if (!entitlement.restsOnPurchase(signal.references())) {
            return Optional.empty();
        }
        if (entitlement.getStatus() == EntitlementStatus.REFUNDED
                || entitlement.isNewerThan(signal.occurredAt())) {
            return Optional.of(EntitlementSignalOutcome.IGNORED_STALE);
        }
        return Optional.empty();
    }

    /**
     * Tells whether a signal may start a new entitlement for an account that has none.
     *
     * <p>A refund cannot: there is nothing to take back. Every other signal can, a subscription
     * status change included - when it arrives before the payment that preceded it, starting the
     * row from it is what lets that older payment be refused as stale instead of resurrecting a
     * cancelled subscription.</p>
     *
     * @param signal what the provider reported
     * @return {@code true} when a new row may be created for it
     */
    public boolean mayStartEntitlement(final EntitlementSignal signal) {
        return signal.kind() != EntitlementSignalKind.PAYMENT_REFUNDED;
    }

    /**
     * Tells whether a signal concerns the purchase an existing entitlement rests on.
     *
     * @param entitlement the existing entitlement
     * @param signal      what the provider reported
     * @return {@code false} when the signal is about some other purchase
     */
    private boolean concernsThisEntitlement(
            final EntitlementEntity entitlement,
            final EntitlementSignal signal) {

        final EntitlementSignalKind kind = signal.kind();
        if (kind == EntitlementSignalKind.PAYMENT_REFUNDED) {
            return entitlement.restsOnPurchase(signal.references());
        }
        if (entitlement.isStandingLifetimePurchase()) {
            return false;
        }
        return entitlement.restsOnSubscription(signal.references().subscriptionId())
                || kind.confirmsSubscriptionPayment();
    }

    /**
     * Makes the entitlement lifetime, first scheduling the cancellation of the subscription it
     * replaces.
     *
     * <p>A subscription already {@link EntitlementStatus#CANCELED} is left alone: nothing more
     * will be charged on it. Every other status - {@link EntitlementStatus#SCHEDULED_CANCEL}
     * included - is cancelled outright, because an immediate cancellation costs nothing when the
     * lifetime purchase already covers the account, and asking twice is harmless.</p>
     *
     * @param entitlement the entitlement to change
     * @param signal      the lifetime purchase
     */
    private void replaceWithLifetimePurchase(
            final EntitlementEntity entitlement,
            final EntitlementSignal signal) {

        if (entitlement.getStatus() != EntitlementStatus.CANCELED) {
            final String droppedSubscriptionId =
                    entitlement.scheduleCancellationOfCurrentSubscription();
            if (droppedSubscriptionId != null) {
                LOGGER.warn("Subscription {} was still waiting to be cancelled when another "
                                + "lifetime purchase replaced a newer subscription; it is no "
                                + "longer retried and has to be cancelled by hand",
                        droppedSubscriptionId);
            }
        }
        entitlement.recordLifetimePurchase(
                signal.provider(), signal.references(), signal.chargedAmount());
    }

    /**
     * Applies a subscription lifecycle signal.
     *
     * <p>The paid-until instant moves only on a confirmed payment, on a scheduled cancellation
     * (which names the day it runs out), or when the row is new and has none. A failure never
     * moves it: were the provider to report the next period on a failed renewal, taking it would
     * hand out a year nobody paid for.</p>
     *
     * @param entitlement    the entitlement to change
     * @param freshlyCreated whether the row was created for this signal
     * @param signal         what the provider reported
     */
    private void applySubscriptionSignal(
            final EntitlementEntity entitlement,
            final boolean freshlyCreated,
            final EntitlementSignal signal) {

        final EntitlementSignalKind kind = signal.kind();
        entitlement.attachToSubscription(signal.provider(), signal.references());
        entitlement.changeStatus(kind.resultingStatus());

        if (freshlyCreated
                || kind.confirmsSubscriptionPayment()
                || kind == EntitlementSignalKind.SUBSCRIPTION_CANCELLATION_SCHEDULED) {
            entitlement.replacePaidUntilWhenReported(signal.paidUntil());
        }
        if (kind.confirmsSubscriptionPayment()) {
            entitlement.replaceChargedAmountWhenReported(signal.chargedAmount());
        }
    }
}
