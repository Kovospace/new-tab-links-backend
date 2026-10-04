package com.kovospace.newtablinks.common.exceptions;

import com.kovospace.newtablinks.common.models.FreePlanLimit;

/**
 * Signals that a write was refused because it would take a free account above a free plan limit.
 *
 * <p>Rendered as HTTP 409 with code {@code FREE_PLAN_LIMIT_REACHED}, {@code limit} and
 * {@code maximum} by {@link GlobalExceptionHandler} - the same shape as
 * {@link FairUseLimitReachedException}, so a client reads both the same way and tells them apart
 * by {@code code}.</p>
 *
 * @since 0.0.17
 */
public class FreePlanLimitReachedException extends RuntimeException {

    /** The machine-readable code the error body carries, part of the API contract. */
    public static final String ERROR_CODE = "FREE_PLAN_LIMIT_REACHED";

    private final FreePlanLimit limit;
    private final int maximum;

    /**
     * Creates the exception.
     *
     * @param limit   the limit the write would have exceeded
     * @param maximum the limit's configured maximum
     */
    public FreePlanLimitReachedException(final FreePlanLimit limit, final int maximum) {
        super("The free plan allows at most %d for %s".formatted(maximum, limit));
        this.limit = limit;
        this.maximum = maximum;
    }

    /**
     * Returns the limit the write would have exceeded.
     *
     * @return the limit
     */
    public FreePlanLimit getLimit() {
        return limit;
    }

    /**
     * Returns the limit's configured maximum.
     *
     * @return the most records a free account may hold
     */
    public int getMaximum() {
        return maximum;
    }
}
