package com.kovospace.newtablinks.common.models;

/**
 * The limits that hold for one account at one moment, with the standing they follow from.
 *
 * <p>Judged live - premium is the account's entitlement at this moment, never a stored flag - so
 * an account whose subscription lapsed is held to the free set from that moment on.</p>
 *
 * <p>{@code fairUse} is the account's own Fair Use set - the global one, raised wherever the
 * operator raised it for this account - which is what {@code values} equals for a premium
 * account, and what a free account would get by upgrading. A refusal compares against it to
 * tell a free-plan limit from a Fair Use one.</p>
 *
 * @param premium whether the account is premium right now
 * @param values  the limits that follow from it
 * @param fairUse the account's Fair Use limits, whatever its standing. Since 0.0.20.
 * @since 0.0.18
 */
public record EffectivePlanLimits(boolean premium, PlanLimitValues values, PlanLimitValues fairUse) {

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
