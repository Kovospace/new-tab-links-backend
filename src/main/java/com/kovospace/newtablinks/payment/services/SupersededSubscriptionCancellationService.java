package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.entitlement.models.SupersededSubscriptionCancellation;
import com.kovospace.newtablinks.entitlement.services.SupersededSubscriptionService;
import com.kovospace.newtablinks.payment.models.SubscriptionCancellationResult;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cancels, at the payment provider, the subscriptions that lifetime purchases replaced.
 *
 * <p><strong>Never inside the webhook's transaction.</strong> The lifetime purchase is committed
 * first and stands whatever the provider answers; the subscription it replaced waits on the
 * entitlement row as a pending cancellation. This service is then called twice over: once right
 * after that commit, and again by a timer for every row still pending.</p>
 *
 * <p><strong>A failure only postpones.</strong> Every failure is logged as a warning naming the
 * subscription, and the row stays pending for the next run - nothing here ever clears it except
 * the provider's confirmation. No exception leaves this class: its callers are an after-commit
 * listener, whose exception would reach the webhook after its work was already stored, and a
 * scheduled job, whose exception would abandon the rest of the batch.</p>
 *
 * <p>Safe on several replicas at once. Two of them cancelling the same subscription cost one
 * redundant call - the second is refused, reads back {@code canceled}, and counts as done - and
 * marking the row is a conditional update that the second one simply finds already done.</p>
 *
 * @since 0.0.9
 */
@Service
public class SupersededSubscriptionCancellationService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SupersededSubscriptionCancellationService.class);

    /** How many pending cancellations one retry run takes on at most. */
    static final int RETRY_BATCH_SIZE = 50;

    private final PaymentSubscriptionCanceller paymentSubscriptionCanceller;
    private final SupersededSubscriptionService supersededSubscriptionService;

    /** Set once the "provider not configured" notice has been logged, so it is logged once. */
    private final AtomicBoolean providerDisabledNoticeLogged = new AtomicBoolean();

    /**
     * Creates the service.
     *
     * @param paymentSubscriptionCanceller  the provider adapter that cancels
     * @param supersededSubscriptionService reads and records the pending state
     */
    public SupersededSubscriptionCancellationService(
            final PaymentSubscriptionCanceller paymentSubscriptionCanceller,
            final SupersededSubscriptionService supersededSubscriptionService) {

        this.paymentSubscriptionCanceller = paymentSubscriptionCanceller;
        this.supersededSubscriptionService = supersededSubscriptionService;
    }

    /**
     * Tries to cancel one superseded subscription, recording it as done when the provider
     * confirms.
     *
     * @param cancellation the pending cancellation
     * @return {@code true} when it is done; {@code false} when it stays pending
     */
    public boolean attemptCancellation(final SupersededSubscriptionCancellation cancellation) {
        if (!isProviderEnabled()) {
            return false;
        }
        try {
            final SubscriptionCancellationResult result =
                    paymentSubscriptionCanceller.cancelImmediately(cancellation.subscriptionId());
            supersededSubscriptionService.markCancelled(cancellation, Instant.now());
            LOGGER.info("Superseded subscription {} of entitlement {}: {}",
                    cancellation.subscriptionId(), cancellation.entitlementId(), result);
            return true;
        } catch (final RuntimeException failure) {
            LOGGER.warn("Could not cancel superseded subscription {} of entitlement {}; it stays "
                            + "pending and will be retried",
                    cancellation.subscriptionId(), cancellation.entitlementId(), failure);
            return false;
        }
    }

    /**
     * Tries every cancellation still pending, up to {@link #RETRY_BATCH_SIZE} of them.
     *
     * @return how many were completed in this run
     */
    public int retryPendingCancellations() {
        if (!isProviderEnabled()) {
            return 0;
        }
        final List<SupersededSubscriptionCancellation> pending =
                supersededSubscriptionService.findPendingCancellations(RETRY_BATCH_SIZE);
        final long completed = pending.stream().filter(this::attemptCancellation).count();
        if (!pending.isEmpty()) {
            LOGGER.info("Retried {} pending subscription cancellations, {} completed",
                    pending.size(), completed);
        }
        return (int) completed;
    }

    /**
     * Tells whether the provider can be called, logging once per process when it cannot.
     *
     * @return {@code false} when the provider's API is not configured
     */
    private boolean isProviderEnabled() {
        if (paymentSubscriptionCanceller.isEnabled()) {
            return true;
        }
        if (providerDisabledNoticeLogged.compareAndSet(false, true)) {
            LOGGER.warn("No payment provider API is configured, so subscriptions replaced by a "
                    + "lifetime purchase cannot be cancelled; they stay pending until one is");
        }
        return false;
    }
}
