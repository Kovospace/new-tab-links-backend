package com.kovospace.newtablinks.entitlement.models;

import java.util.Objects;
import java.util.UUID;

/**
 * A subscription that a lifetime purchase replaced and that still has to be cancelled at the
 * payment provider, so that it never charges again.
 *
 * <p>Published as an application event when a lifetime purchase schedules one - its listener runs
 * only after the webhook's transaction commits - and returned by the query the retry job works
 * from. Either way it names the row, so that marking it done can check the row still waits on
 * this very subscription.</p>
 *
 * @param entitlementId  the entitlement row holding the pending cancellation
 * @param subscriptionId the provider's identifier of the subscription to cancel
 * @since 0.0.9
 */
public record SupersededSubscriptionCancellation(UUID entitlementId, String subscriptionId) {

    /**
     * Rejects a cancellation missing either half.
     *
     * @throws NullPointerException when the row or the subscription is missing
     */
    public SupersededSubscriptionCancellation {
        Objects.requireNonNull(entitlementId, "entitlementId");
        Objects.requireNonNull(subscriptionId, "subscriptionId");
    }
}
