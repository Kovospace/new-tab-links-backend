package com.kovospace.newtablinks.statistics.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes the previous days' website visitor hashes every night.
 *
 * <p>A hash is only useful on its own day, and keeping it longer would only keep something about
 * a visitor for no purpose. A separate class from {@link WebsiteVisitService}, as with the visitor
 * token cleanup, so that the decision to run on a schedule lives in one obvious place.</p>
 *
 * <p>Safe on several replicas: the delete is one statement judged by date, so a second pod
 * removes nothing. A pod that is down at midnight leaves the rows for the next night's run, and
 * they can no longer match anything in the meantime - the day is part of the hash.</p>
 *
 * @since 0.0.11
 */
@Component
public class WebsiteVisitorHashCleanupScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WebsiteVisitorHashCleanupScheduler.class);

    private final WebsiteVisitService websiteVisitService;

    /**
     * Creates the scheduler.
     *
     * @param websiteVisitService the service that owns the deletion
     */
    public WebsiteVisitorHashCleanupScheduler(final WebsiteVisitService websiteVisitService) {
        this.websiteVisitService = websiteVisitService;
    }

    /**
     * Removes every hash from before today.
     */
    @Scheduled(cron = "${newtablinks.statistics.visitor-hash-cleanup-cron}", zone = "UTC")
    public void deletePreviousDaysVisitorHashes() {
        final int deletedHashes = websiteVisitService.forgetVisitorsBeforeToday();
        if (deletedHashes > 0) {
            LOGGER.info("Deleted {} website visitor hashes from previous days", deletedHashes);
        }
    }
}
