package com.kovospace.newtablinks.statistics.services;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kovospace.newtablinks.common.exceptions.RequestRateLimitExceededException;
import com.kovospace.newtablinks.statistics.config.UsageStatisticsProperties;
import com.kovospace.newtablinks.statistics.models.UsageMetric;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the per-address limit on the public statistics endpoints.
 *
 * @since 0.0.11
 */
class UsageStatisticsRateLimiterTest {

    private static final int LIMIT = 3;
    private static final int WEBSITE_VISIT_LIMIT = 5;
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final String ADDRESS = "203.0.113.7";

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-09-28T10:00:00Z"));
    private final UsageStatisticsRateLimiter rateLimiter = new UsageStatisticsRateLimiter(
            new UsageStatisticsProperties(900, LIMIT, WEBSITE_VISIT_LIMIT, WINDOW), clock);

    @Test
    @DisplayName("lets the limit through and refuses the next request, saying when to retry")
    void shouldRefuseTheRequestAfterTheLimit() {
        acquireLimit(UsageMetric.NEW_TABS, ADDRESS);
        clock.advance(Duration.ofMinutes(20));

        assertThatThrownBy(() -> rateLimiter.acquirePermit(UsageMetric.NEW_TABS, ADDRESS))
                .isInstanceOf(RequestRateLimitExceededException.class)
                .extracting(refusal -> ((RequestRateLimitExceededException) refusal).getRetryAfter())
                .isEqualTo(Duration.ofMinutes(40));
    }

    @Test
    @DisplayName("lets the address in again once its window has closed")
    void shouldAllowAgainAfterTheWindowCloses() {
        acquireLimit(UsageMetric.NEW_TABS, ADDRESS);
        clock.advance(WINDOW);

        assertThatCode(() -> rateLimiter.acquirePermit(UsageMetric.NEW_TABS, ADDRESS))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("counts each address and each endpoint separately")
    void shouldCountAddressesAndEndpointsSeparately() {
        acquireLimit(UsageMetric.NEW_TABS, ADDRESS);

        assertThatCode(() -> rateLimiter.acquirePermit(UsageMetric.WEBSITE_VISITORS, ADDRESS))
                .doesNotThrowAnyException();
        assertThatCode(() -> rateLimiter.acquirePermit(UsageMetric.NEW_TABS, "198.51.100.1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("gives website visits their own, higher limit")
    void shouldApplyTheWebsiteVisitLimitToWebsiteVisits() {
        for (int request = 0; request < WEBSITE_VISIT_LIMIT; request++) {
            rateLimiter.acquirePermit(UsageMetric.WEBSITE_VISITORS, ADDRESS);
        }

        assertThatThrownBy(() -> rateLimiter.acquirePermit(UsageMetric.WEBSITE_VISITORS, ADDRESS))
                .isInstanceOf(RequestRateLimitExceededException.class);
    }

    @Test
    @DisplayName("keeps refusing a caller that goes on sending, without the count overflowing")
    void shouldKeepRefusingPersistentCaller() {
        acquireLimit(UsageMetric.NEW_TABS, ADDRESS);
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThatThrownBy(() -> rateLimiter.acquirePermit(UsageMetric.NEW_TABS, ADDRESS))
                    .isInstanceOf(RequestRateLimitExceededException.class);
        }
    }

    /**
     * Uses up an address's whole allowance on one endpoint.
     *
     * @param endpoint the endpoint
     * @param address  the address
     */
    private void acquireLimit(final UsageMetric endpoint, final String address) {
        for (int request = 0; request < LIMIT; request++) {
            rateLimiter.acquirePermit(endpoint, address);
        }
    }

    /**
     * A clock the test moves by hand.
     */
    private static final class AdjustableClock extends Clock {

        private Instant now;

        /**
         * Creates the clock.
         *
         * @param start the moment it shows first
         */
        AdjustableClock(final Instant start) {
            this.now = start;
        }

        /**
         * Moves the clock forward.
         *
         * @param amount how far
         */
        void advance(final Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
