package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.common.exceptions.PaymentProviderRequestFailedException;
import com.kovospace.newtablinks.payment.models.SubscriptionCancellationResult;

/**
 * The outbound half of the payment port that stops a subscription from ever charging again.
 *
 * <p>Used for one thing only: cancelling the subscription a lifetime purchase replaced. The
 * account is already covered for life, so the cancellation is immediate and nothing is
 * refunded.</p>
 *
 * @since 0.0.9
 */
public interface PaymentSubscriptionCanceller {

    /**
     * Tells whether the provider can be called at all on this server.
     *
     * @return {@code false} when the provider's API is not configured, in which case
     *         {@link #cancelImmediately} must not be called
     */
    boolean isEnabled();

    /**
     * Cancels a subscription at once, with no refund.
     *
     * <p>Idempotent from the caller's point of view: asking again for a subscription that is
     * already cancelled answers {@link SubscriptionCancellationResult#ALREADY_NOT_RENEWING}
     * rather than failing.</p>
     *
     * @param subscriptionId the provider's subscription identifier
     * @return how it ended
     * @throws PaymentProviderRequestFailedException when the provider refuses and the
     *                                               subscription is still live, or it cannot be
     *                                               reached; the caller keeps it pending and
     *                                               retries
     */
    SubscriptionCancellationResult cancelImmediately(String subscriptionId);
}
