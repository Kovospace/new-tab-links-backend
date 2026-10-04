package com.kovospace.newtablinks.common.models;

/**
 * The limits that hold for one account at one moment, with the standing they follow from.
 *
 * <p>Judged live - premium is the account's entitlement at this moment, never a stored flag - so
 * an account whose subscription lapsed is held to the free set from that moment on.</p>
 *
 * @param premium whether the account is premium right now
 * @param values  the limits that follow from it
 * @since 0.0.18
 */
public record EffectivePlanLimits(boolean premium, PlanLimitValues values) {

    /**
     * Returns the maximum of one limit.
     *
     * @param limit the limit
     * @return the most records of that kind the account may hold
     */
    public int maximumFor(final PlanLimit limit) {
        return values.maximumFor(limit);
    }
}
