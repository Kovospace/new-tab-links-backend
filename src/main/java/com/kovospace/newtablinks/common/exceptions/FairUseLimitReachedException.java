package com.kovospace.newtablinks.common.exceptions;

import com.kovospace.newtablinks.common.models.FairUseLimit;

/**
 * Signals that a write was refused because it would take a count above a Fair Use Policy cap.
 *
 * <p>Rendered as HTTP 409 with code {@code FAIR_USE_LIMIT_REACHED}, {@code limit} and
 * {@code maximum} by {@link GlobalExceptionHandler} - for an interactive create and for a whole
 * sync push alike.</p>
 *
 * @since 0.0.16
 */
public class FairUseLimitReachedException extends RuntimeException {

    /** The machine-readable code the error body carries, part of the API contract. */
    public static final String ERROR_CODE = "FAIR_USE_LIMIT_REACHED";

    private final FairUseLimit limit;
    private final int maximum;

    /**
     * Creates the exception.
     *
     * @param limit   the cap the write would have exceeded
     * @param maximum the cap's configured maximum
     */
    public FairUseLimitReachedException(final FairUseLimit limit, final int maximum) {
        super("The Fair Use Policy allows at most %d for %s".formatted(maximum, limit));
        this.limit = limit;
        this.maximum = maximum;
    }

    /**
     * Returns the cap the write would have exceeded.
     *
     * @return the cap
     */
    public FairUseLimit getLimit() {
        return limit;
    }

    /**
     * Returns the cap's configured maximum.
     *
     * @return the most records the cap allows
     */
    public int getMaximum() {
        return maximum;
    }
}
