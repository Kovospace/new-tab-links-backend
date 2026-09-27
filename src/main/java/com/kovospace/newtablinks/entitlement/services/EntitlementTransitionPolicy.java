package com.kovospace.newtablinks.entitlement.services;

import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignal;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalKind;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalOutcome;
import org.springframework.stereotype.Component;

/**
 * Decides what one payment signal does to one entitlement, and does it.
 *
 * <p>Pure: no persistence, no clock, no provider. The rules, in the order they are checked:</p>
 * <ol>
 *   <li><strong>Older than what was applied last - refused.</strong> Webhooks arrive out of
 *       order; a {@code paid} can land after the {@code past_due} that logically followed it,
 *       and applying it would roll the row back.</li>
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
 * @since 0.0.9
 */
@Component
public class EntitlementTransitionPolicy {

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

        if (!freshlyCreated && entitlement.isNewerThan(signal.occurredAt())) {
            return EntitlementSignalOutcome.IGNORED_STALE;
        }
        if (!freshlyCreated && !concernsThisEntitlement(entitlement, signal)) {
            return EntitlementSignalOutcome.IGNORED_UNRELATED;
        }

        switch (signal.kind()) {
            case LIFETIME_PURCHASED -> entitlement.recordLifetimePurchase(
                    signal.provider(), signal.references(), signal.chargedAmount());
            case PAYMENT_REFUNDED -> entitlement.changeStatus(signal.kind().resultingStatus());
            default -> applySubscriptionSignal(entitlement, freshlyCreated, signal);
        }
        entitlement.markProviderEventApplied(signal.occurredAt());
        return EntitlementSignalOutcome.APPLIED;
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
        if (kind == EntitlementSignalKind.LIFETIME_PURCHASED) {
            return true;
        }
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
