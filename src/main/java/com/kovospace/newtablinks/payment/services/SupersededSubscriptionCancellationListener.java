package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.entitlement.models.SupersededSubscriptionCancellation;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Makes the first attempt at cancelling a superseded subscription, as soon as the lifetime
 * purchase that replaced it has committed.
 *
 * <p>Bound to the commit, so a rolled-back webhook never cancels anything, and the provider is
 * never called while the entitlement row is locked. It runs on the webhook's own thread, so the
 * acknowledgement waits for it - at most the Creem API timeout, and the webhook's work is already
 * stored by then. When it fails, the retry job takes over.</p>
 *
 * @since 0.0.9
 */
@Component
public class SupersededSubscriptionCancellationListener {

    private final SupersededSubscriptionCancellationService cancellationService;

    /**
     * Creates the listener.
     *
     * @param cancellationService does the cancelling
     */
    public SupersededSubscriptionCancellationListener(
            final SupersededSubscriptionCancellationService cancellationService) {

        this.cancellationService = cancellationService;
    }

    /**
     * Tries the cancellation once the transaction that scheduled it has committed.
     *
     * @param cancellation the pending cancellation the lifetime purchase left behind
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void cancelAfterCommit(final SupersededSubscriptionCancellation cancellation) {
        cancellationService.attemptCancellation(cancellation);
    }
}
