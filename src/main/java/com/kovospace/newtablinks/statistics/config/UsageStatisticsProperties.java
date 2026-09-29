package com.kovospace.newtablinks.statistics.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the anonymous usage counts are collected, bound from {@code newtablinks.statistics.*}.
 *
 * @param reportIntervalSeconds                how long the extension waits before its next
 *                                             new-tab report; returned in every acknowledgement,
 *                                             which is how the interval is tuned without an
 *                                             extension release
 * @param rateLimitMaximumRequests             new-tab reports one address may make within
 *                                             {@code rateLimitWindow}
 * @param websiteVisitRateLimitMaximumRequests website visits one address may report within
 *                                             {@code rateLimitWindow}; far higher than the
 *                                             new-tab limit, because every visitor behind a
 *                                             carrier's shared address draws on it
 * @param rateLimitWindow                      length of the rate limit's counting window
 * @since 0.0.11
 */
@ConfigurationProperties(prefix = "newtablinks.statistics")
public record UsageStatisticsProperties(
        int reportIntervalSeconds,
        int rateLimitMaximumRequests,
        int websiteVisitRateLimitMaximumRequests,
        Duration rateLimitWindow) {

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
        if (websiteVisitRateLimitMaximumRequests <= 0) {
            throw new IllegalArgumentException("newtablinks.statistics."
                    + "website-visit-rate-limit-maximum-requests must be positive");
        }
        if (rateLimitWindow == null || rateLimitWindow.isZero() || rateLimitWindow.isNegative()) {
            throw new IllegalArgumentException(
                    "newtablinks.statistics.rate-limit-window must be a positive duration");
        }
    }
}
