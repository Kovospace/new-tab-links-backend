package com.kovospace.newtablinks.entitlement.models;

/**
 * What a payment provider reported, in terms that do not depend on which provider said it.
 *
 * @since 0.0.9
 */
public enum EntitlementSignalKind {

    /** A one-time purchase of the lifetime entitlement was paid. */
    LIFETIME_PURCHASED(false, EntitlementStatus.ACTIVE),

    /** A subscription was bought and its first period paid. */
    SUBSCRIPTION_PURCHASED(true, EntitlementStatus.ACTIVE),

    /** A subscription period was paid - the first one, or a renewal. */
    SUBSCRIPTION_PAID(true, EntitlementStatus.ACTIVE),

    /** The provider reports a subscription as active, for instance after a resumed cancellation. */
    SUBSCRIPTION_ACTIVATED(true, EntitlementStatus.ACTIVE),

    /** A renewal payment failed and is being retried. Marks, never revokes. */
    SUBSCRIPTION_PAYMENT_FAILED(false, EntitlementStatus.PAST_DUE),

    /** The subscription will end with the current period. */
    SUBSCRIPTION_CANCELLATION_SCHEDULED(false, EntitlementStatus.SCHEDULED_CANCEL),

    /** The subscription was cancelled. */
    SUBSCRIPTION_CANCELED(false, EntitlementStatus.CANCELED),

    /** The subscription's period ended without a new payment. */
    SUBSCRIPTION_EXPIRED(false, EntitlementStatus.EXPIRED),

    /** A payment was refunded in full. */
    PAYMENT_REFUNDED(false, EntitlementStatus.REFUNDED);

    private final boolean confirmsSubscriptionPayment;
    private final EntitlementStatus resultingStatus;

    /**
     * Declares a kind.
     *
     * @param confirmsSubscriptionPayment whether the signal says money arrived for a
     *                                    subscription, which is what allows it to take over an
     *                                    entitlement resting on something else, and to move the
     *                                    paid-until instant
     * @param resultingStatus             the status an entitlement is in once the signal applied
     */
    EntitlementSignalKind(
            final boolean confirmsSubscriptionPayment,
            final EntitlementStatus resultingStatus) {

        this.confirmsSubscriptionPayment = confirmsSubscriptionPayment;
        this.resultingStatus = resultingStatus;
    }

    /**
     * Tells whether the signal confirms a subscription payment.
     *
     * @return {@code true} when money arrived for a subscription
     */
    public boolean confirmsSubscriptionPayment() {
        return confirmsSubscriptionPayment;
    }

    /**
     * Returns the status an entitlement is in once a signal of this kind has been applied.
     *
     * @return the resulting status
     */
    public EntitlementStatus resultingStatus() {
        return resultingStatus;
    }
}
