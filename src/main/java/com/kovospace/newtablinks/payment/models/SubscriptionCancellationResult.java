package com.kovospace.newtablinks.payment.models;

/**
 * How a request to cancel a subscription at the payment provider ended, when it did not fail.
 *
 * <p>Both mean the same thing to the caller - the subscription will never charge again, so the
 * pending cancellation is done - and are told apart only for the log.</p>
 *
 * @since 0.0.9
 */
public enum SubscriptionCancellationResult {

    /** The provider accepted the cancellation just now. */
    CANCELLED,

    /**
     * The provider refused, but the subscription was already ended - or already scheduled to end
     * without another charge - so there was nothing left to cancel.
     */
    ALREADY_NOT_RENEWING
}
