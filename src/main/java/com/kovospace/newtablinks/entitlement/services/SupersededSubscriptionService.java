package com.kovospace.newtablinks.entitlement.services;

import com.kovospace.newtablinks.entitlement.models.SupersededSubscriptionCancellation;
import com.kovospace.newtablinks.entitlement.repositories.EntitlementRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The entitlement module's side of cancelling subscriptions a lifetime purchase replaced: which
 * ones are still pending, and recording that one is done.
 *
 * <p>Provider-neutral like the rest of the module. Asking the provider is the payment module's
 * job; this only reads and writes the two columns that hold the pending state.</p>
 *
 * @since 0.0.9
 */
@Service
public class SupersededSubscriptionService {

    private final EntitlementRepository entitlementRepository;

    /**
     * Creates the service.
     *
     * @param entitlementRepository persistence access for entitlements
     */
    public SupersededSubscriptionService(final EntitlementRepository entitlementRepository) {
        this.entitlementRepository = entitlementRepository;
    }

    /**
     * Lists the superseded subscriptions still waiting to be cancelled, the longest-waiting first.
     *
     * @param maximumCount how many to return at most
     * @return the pending cancellations, possibly empty
     */
    @Transactional(readOnly = true)
    public List<SupersededSubscriptionCancellation> findPendingCancellations(final int maximumCount) {
        return entitlementRepository.findPendingSupersededSubscriptionCancellations(
                Limit.of(maximumCount));
    }

    /**
     * Records that the provider confirmed a superseded subscription cancelled.
     *
     * <p>Always in a transaction of its own: it is called from an after-commit listener, where the
     * webhook's transaction has already committed and joining it would write nothing.</p>
     *
     * @param cancellation the cancellation that succeeded
     * @param confirmedAt  when the provider confirmed it
     * @return {@code true} when the row was marked; {@code false} when it no longer waited on that
     *         subscription - already marked by another replica, or superseded again meanwhile
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markCancelled(
            final SupersededSubscriptionCancellation cancellation,
            final Instant confirmedAt) {

        return entitlementRepository.markSupersededSubscriptionCancelled(
                cancellation.entitlementId(), cancellation.subscriptionId(), confirmedAt) == 1;
    }
}
