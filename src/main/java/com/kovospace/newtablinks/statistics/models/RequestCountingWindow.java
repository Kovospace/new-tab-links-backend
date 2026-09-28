package com.kovospace.newtablinks.statistics.models;

import java.time.Duration;
import java.time.Instant;

/**
 * How many requests one caller has made in its current rate-limit window.
 *
 * @param endsAt       the moment the window closes and counting starts again
 * @param requestCount requests made in it so far, including the current one
 * @since 0.0.11
 */
public record RequestCountingWindow(Instant endsAt, int requestCount) {

    /**
     * Opens a window counting its first request.
     *
     * @param now    the moment of the first request
     * @param length how long the window lasts
     * @return the new window
     */
    public static RequestCountingWindow openedAt(final Instant now, final Duration length) {
        return new RequestCountingWindow(now.plus(length), 1);
    }

    /**
     * Whether the window has closed.
     *
     * @param now the moment to judge by
     * @return {@code true} from {@link #endsAt()} onwards
     */
    public boolean hasEndedBy(final Instant now) {
        return !now.isBefore(endsAt);
    }

    /**
     * Counts one more request, never past {@code ceiling}.
     *
     * <p>The ceiling keeps a caller that goes on sending after being refused from counting
     * towards an overflow; one past the limit already means "refused".</p>
     *
     * @param ceiling the highest count worth holding
     * @return the window with the request counted
     */
    public RequestCountingWindow withOneMoreRequest(final int ceiling) {
        return new RequestCountingWindow(endsAt, Math.min(requestCount + 1, ceiling));
    }
}
