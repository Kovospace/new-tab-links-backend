package com.kovospace.newtablinks.auth.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes expired visitor tokens on a timer.
 *
 * <p>These rows are produced one per page load and are worthless the moment they expire, so
 * without a sweep the table grows without limit for no benefit at all. It is a separate class
 * from {@link VisitorTokenService} so that the service stays a plain collaborator anything can
 * call, and the decision to run it on a schedule lives in exactly one obvious place.</p>
 *
 * <p>Safe to run on several replicas at once: the delete is a single statement judged by expiry,
 * so a second pod either removes the rows the first one has not reached yet, or removes
 * nothing.</p>
 *
 * @since 0.0.5
 */
@Component
public class VisitorTokenCleanupScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(VisitorTokenCleanupScheduler.class);

    private final VisitorTokenService visitorTokenService;

    /**
     * Creates the scheduler.
     *
     * @param visitorTokenService the service that owns the deletion
     */
    public VisitorTokenCleanupScheduler(final VisitorTokenService visitorTokenService) {
        this.visitorTokenService = visitorTokenService;
    }

    /**
     * Removes every expired token.
     *
     * <p>Scheduled with a fixed <em>delay</em> rather than a fixed rate, so a slow sweep on a
     * large table can never queue up behind itself.</p>
     */
    @Scheduled(
            fixedDelayString = "${newtablinks.visitor-token.cleanup-interval}",
            initialDelayString = "${newtablinks.visitor-token.cleanup-interval}")
    public void deleteExpiredVisitorTokens() {
        final int deletedTokens = visitorTokenService.deleteExpiredTokens();
        if (deletedTokens > 0) {
            LOGGER.info("Deleted {} expired visitor tokens", deletedTokens);
        }
    }
}
