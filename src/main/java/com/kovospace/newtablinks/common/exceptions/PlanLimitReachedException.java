package com.kovospace.newtablinks.common.exceptions;

import com.kovospace.newtablinks.common.models.PlanLimit;
import com.kovospace.newtablinks.common.models.PlanLimitRefusalCode;

/**
 * Signals that a request was refused because the account's plan does not allow it - one more
 * record than a limit, a write into a profile or workspace that holds no synchronisation slot, or
 * one more signed-in installation.
 *
 * <p>Rendered as HTTP 409 by {@link GlobalExceptionHandler}, with {@code code}, {@code limit},
 * {@code maximum} and {@code manageUrl} at the top level of the error body. Build it through
 * {@code PlanLimitPolicy#refusalOf}, which chooses the code and knows the website's address.</p>
 *
 * @since 0.0.18
 */
public class PlanLimitReachedException extends RuntimeException {

    private final PlanLimitRefusalCode code;
    private final PlanLimit limit;
    private final int maximum;
    private final String manageUrl;

    /**
     * Creates the exception.
     *
     * @param code      which rule set refused the request
     * @param limit     the limit the request would have exceeded
     * @param maximum   that limit's maximum for this account
     * @param manageUrl absolute address of the website's devices page, where the user can see
     *                  what synchronises and sign installations out
     */
    public PlanLimitReachedException(
            final PlanLimitRefusalCode code,
            final PlanLimit limit,
            final int maximum,
            final String manageUrl) {

        super("%s: the account's plan allows at most %d for %s".formatted(code, maximum, limit));
        this.code = code;
        this.limit = limit;
        this.maximum = maximum;
        this.manageUrl = manageUrl;
    }

    /**
     * Returns which rule set refused the request.
     *
     * @return the code
     */
    public PlanLimitRefusalCode getCode() {
        return code;
    }

    /**
     * Returns the limit the request would have exceeded.
     *
     * @return the limit
     */
    public PlanLimit getLimit() {
        return limit;
    }

    /**
     * Returns the limit's maximum for this account.
     *
     * @return the most records of that kind the account may hold
     */
    public int getMaximum() {
        return maximum;
    }

    /**
     * Returns the website's devices page.
     *
     * @return an absolute URL
     */
    public String getManageUrl() {
        return manageUrl;
    }
}
