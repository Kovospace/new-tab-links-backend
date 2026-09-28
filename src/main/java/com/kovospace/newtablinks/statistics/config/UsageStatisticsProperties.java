package com.kovospace.newtablinks.statistics.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the anonymous usage counts are collected, bound from {@code newtablinks.statistics.*}.
 *
 * @param reportIntervalSeconds          how long the extension waits before its next new-tab
 *                                       report; returned in every acknowledgement, which is how the
 *                                       interval is tuned without an extension release
 * @param visitorHashSecret              key of the daily website visitor hash; blank makes
 *                                       {@link com.kovospace.newtablinks.statistics.services.WebsiteVisitorHasher}
 *                                       generate one per process, which dedupes per pod only
 * @param rateLimitMaximumRequests       requests one address may make to one statistics endpoint
 *                                       within {@code rateLimitWindow}
 * @param rateLimitWindow                length of the rate limit's counting window
 * @param visitorHashCleanupCron         when the previous days' visitor hashes are deleted, as a
 *                                       Spring cron expression evaluated in UTC
 * @since 0.0.11
 */
@ConfigurationProperties(prefix = "newtablinks.statistics")
public record UsageStatisticsProperties(
        int reportIntervalSeconds,
        String visitorHashSecret,
        int rateLimitMaximumRequests,
        Duration rateLimitWindow,
        String visitorHashCleanupCron) {

    /**
     * Rejects a configuration that could not work, at startup rather than per request.
     *
     * <p>A non-positive interval would tell every extension to report continuously, and a
     * non-positive limit or window would refuse every report while looking configured.</p>
     */
    public UsageStatisticsProperties {
        if (reportIntervalSeconds <= 0) {
            throw new IllegalArgumentException(
                    "newtablinks.statistics.report-interval-seconds must be positive");
        }
        if (rateLimitMaximumRequests <= 0) {
            throw new IllegalArgumentException(
                    "newtablinks.statistics.rate-limit-maximum-requests must be positive");
        }
        if (rateLimitWindow == null || rateLimitWindow.isZero() || rateLimitWindow.isNegative()) {
            throw new IllegalArgumentException(
                    "newtablinks.statistics.rate-limit-window must be a positive duration");
        }
    }

    /**
     * Whether a visitor hash key was configured, rather than left for each process to invent.
     *
     * @return {@code true} when the secret is present and not blank
     */
    public boolean isVisitorHashSecretConfigured() {
        return visitorHashSecret != null && !visitorHashSecret.isBlank();
    }
}
