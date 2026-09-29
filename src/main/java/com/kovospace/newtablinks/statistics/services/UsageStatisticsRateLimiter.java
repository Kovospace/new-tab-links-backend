package com.kovospace.newtablinks.statistics.services;

import com.kovospace.newtablinks.common.exceptions.RequestRateLimitExceededException;
import com.kovospace.newtablinks.statistics.config.UsageStatisticsProperties;
import com.kovospace.newtablinks.statistics.models.RequestCountingWindow;
import com.kovospace.newtablinks.statistics.models.UsageMetric;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Limits how often one address may call a public statistics endpoint.
 *
 * <p><strong>The one place this application limits by address</strong>, which it otherwise
 * refuses to do because carrier-grade NAT puts whole neighbourhoods behind one address. It is
 * acceptable here because being refused costs a real user nothing: the extension keeps unsent
 * counts and reports them later, and a visit that goes uncounted is invisible to the visitor.
 * What it buys is that one script cannot inflate the totals at network speed.</p>
 *
 * <p>Uncounted is still not free for the <em>count</em>, so each endpoint - named by the metric
 * it feeds - has its own count and its own limit. New-tab reports default to 120 an hour, room
 * for about thirty installations reporting every fifteen minutes from one address. Website
 * visits default far higher: every visitor behind a carrier's shared address reports from it,
 * and a tight limit there would silently drop real people. A fixed window per address: simple,
 * and the burst it allows at a window's edge is harmless for counting.</p>
 *
 * <p><strong>Held in memory, so per replica</strong>, like the admin sign-in lockout. The
 * addresses live only here, for at most one window, and are never written or logged. The map is
 * pruned of closed windows once a window, and stops tracking new addresses at
 * {@link #MAXIMUM_TRACKED_CALLERS}; past that it lets them through rather than grow without
 * bound, because an uncounted report is cheaper than an exhausted heap.</p>
 *
 * @since 0.0.11
 */
@Component
public class UsageStatisticsRateLimiter {

    /** Most address-and-endpoint pairs tracked at once. */
    static final int MAXIMUM_TRACKED_CALLERS = 100_000;

    private final ConcurrentMap<String, RequestCountingWindow> windowsByCaller =
            new ConcurrentHashMap<>();
    private final Map<UsageMetric, Integer> maximumRequestsPerWindowByEndpoint;
    private final Duration windowLength;
    private final Clock clock;

    /** The next moment closed windows are swept out of {@link #windowsByCaller}. */
    private volatile Instant nextPruneAt;

    /**
     * Creates the limiter.
     *
     * @param usageStatisticsProperties the limit and the window it applies over
     * @param usageStatisticsClock      the clock windows are measured by
     */
    public UsageStatisticsRateLimiter(
            final UsageStatisticsProperties usageStatisticsProperties,
            final Clock usageStatisticsClock) {

        this.maximumRequestsPerWindowByEndpoint = new EnumMap<>(Map.of(
                UsageMetric.NEW_TABS, usageStatisticsProperties.rateLimitMaximumRequests(),
                UsageMetric.WEBSITE_VISITORS,
                usageStatisticsProperties.websiteVisitRateLimitMaximumRequests()));
        this.windowLength = usageStatisticsProperties.rateLimitWindow();
        this.clock = usageStatisticsClock;
        this.nextPruneAt = clock.instant().plus(windowLength);
    }

    /**
     * Counts a request, refusing it when the address has used up its window on that endpoint.
     *
     * @param endpoint      the endpoint called, named by the metric it feeds
     * @param clientAddress the caller's address as resolved behind the ingress
     * @throws RequestRateLimitExceededException when the address is over the limit; carries how
     *                                           long until its window closes
     */
    public void acquirePermit(final UsageMetric endpoint, final String clientAddress) {
        final Instant now = clock.instant();
        pruneClosedWindowsWhenDue(now);

        final String callerKey = endpoint.getIdentifier() + '|' + clientAddress;
        if (!windowsByCaller.containsKey(callerKey)
                && windowsByCaller.size() >= MAXIMUM_TRACKED_CALLERS) {
            return;
        }
        final int maximumRequestsPerWindow = maximumRequestsPerWindowByEndpoint.get(endpoint);
        final RequestCountingWindow window = windowsByCaller.compute(callerKey,
                (key, current) -> countRequest(current, now, maximumRequestsPerWindow));
        if (window.requestCount() > maximumRequestsPerWindow) {
            throw new RequestRateLimitExceededException(Duration.between(now, window.endsAt()));
        }
    }

    /**
     * Adds a request to a caller's window, opening a fresh one when there is none or it closed.
     *
     * @param current                  the caller's window, or {@code null} when it has none
     * @param now                      the moment of the request
     * @param maximumRequestsPerWindow the endpoint's limit, past which the count stops growing
     * @return the window with the request counted
     */
    private RequestCountingWindow countRequest(
            final RequestCountingWindow current,
            final Instant now,
            final int maximumRequestsPerWindow) {

        if (current == null || current.hasEndedBy(now)) {
            return RequestCountingWindow.openedAt(now, windowLength);
        }
        return current.withOneMoreRequest(maximumRequestsPerWindow + 1);
    }

    /**
     * Forgets every closed window, at most once per window length.
     *
     * <p>Two threads may both decide to sweep; the second finds nothing left to remove.</p>
     *
     * @param now the current moment
     */
    private void pruneClosedWindowsWhenDue(final Instant now) {
        if (now.isBefore(nextPruneAt)) {
            return;
        }
        nextPruneAt = now.plus(windowLength);
        windowsByCaller.values().removeIf(window -> window.hasEndedBy(now));
    }
}
