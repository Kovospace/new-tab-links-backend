package com.kovospace.newtablinks.payment.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries, on a timer, every cancellation of a superseded subscription still pending.
 *
 * <p>This is what makes the cancellation durable: the attempt right after the webhook commits can
 * fail - the provider down, the process killed in between - and the pending state on the
 * entitlement row is what this picks up again. A separate class from the service for the same
 * reason as {@code VisitorTokenCleanupScheduler}: the decision to run it on a schedule lives in
 * one obvious place. Safe to run on several replicas at once; see
 * {@link SupersededSubscriptionCancellationService}.</p>
 *
 * @since 0.0.9
 */
@Component
public class SupersededSubscriptionCancellationScheduler {

    private final SupersededSubscriptionCancellationService cancellationService;

    /**
     * Creates the scheduler.
     *
     * @param cancellationService does the cancelling
     */
    public SupersededSubscriptionCancellationScheduler(
            final SupersededSubscriptionCancellationService cancellationService) {

        this.cancellationService = cancellationService;
    }

    /**
     * Retries the pending cancellations.
     *
     * <p>Fixed delay rather than fixed rate, so a slow provider can never queue runs up behind
     * each other.</p>
     */
    @Scheduled(
            fixedDelayString = "${newtablinks.payment.subscription-cancellation-retry-interval}",
            initialDelayString = "${newtablinks.payment.subscription-cancellation-retry-interval}")
    public void retryPendingSubscriptionCancellations() {
        cancellationService.retryPendingCancellations();
    }
}
