package com.kovospace.newtablinks.fairuse.models;

import com.kovospace.newtablinks.common.models.PlanLimit;
import com.kovospace.newtablinks.common.models.PlanLimitValues;
import java.util.Arrays;
import java.util.Objects;

/**
 * The Fair Use limits the operator raised for one account - each one a number, or {@code null}
 * where the account keeps the global value.
 *
 * <p>An override only ever raises: {@link #raise(PlanLimitValues)} takes the higher of the
 * override and the global value, so a global value that later rises past an override wins, and
 * nothing here can lower a limit below the global Fair Use one.</p>
 *
 * @param profiles             raised profile limit, or {@code null}
 * @param workspacesPerProfile raised workspaces-per-profile limit, or {@code null}
 * @param groupsPerWorkspace   raised groups-per-workspace limit, or {@code null}
 * @param subgroupsPerGroup    raised subgroups-per-group limit, or {@code null}
 * @param linksPerWorkspace    raised links-per-workspace limit, or {@code null}
 * @param closedTabs           raised closed-tab history limit, or {@code null}
 * @param browsingHistory      raised browsing history limit, or {@code null}
 * @param devices              raised signed-in installation limit, or {@code null}
 * @since 0.0.20
 */
public record FairUseLimitOverrides(
        Integer profiles,
        Integer workspacesPerProfile,
        Integer groupsPerWorkspace,
        Integer subgroupsPerGroup,
        Integer linksPerWorkspace,
        Integer closedTabs,
        Integer browsingHistory,
        Integer devices) {

    /** No limit raised at all - what an account without an override has. */
    public static final FairUseLimitOverrides NONE =
            new FairUseLimitOverrides(null, null, null, null, null, null, null, null);

    /**
     * Tells whether no limit is raised.
     *
     * @return {@code true} when every limit is {@code null}
     */
    public boolean isEmpty() {
        return Arrays.stream(PlanLimit.values()).map(this::overrideFor).allMatch(Objects::isNull);
    }

    /**
     * Returns the override of one limit.
     *
     * @param limit the limit
     * @return the raised maximum, or {@code null} when that limit is not raised
     */
    public Integer overrideFor(final PlanLimit limit) {
        return switch (limit) {
            case PROFILES -> profiles;
            case WORKSPACES_PER_PROFILE -> workspacesPerProfile;
            case GROUPS_PER_WORKSPACE -> groupsPerWorkspace;
            case SUBGROUPS_PER_GROUP -> subgroupsPerGroup;
            case LINKS_PER_WORKSPACE -> linksPerWorkspace;
            case CLOSED_TABS -> closedTabs;
            case BROWSING_HISTORY -> browsingHistory;
            case DEVICES -> devices;
        };
    }

    /**
     * Lays these overrides over a set of global limits, keeping the higher value of each.
     *
     * @param globalLimits the global Fair Use limits
     * @return the account's Fair Use limits: each one the global value, or the override where it
     *         is set and higher
     */
    public PlanLimitValues raise(final PlanLimitValues globalLimits) {
        return new PlanLimitValues(
                raised(globalLimits, PlanLimit.PROFILES),
                raised(globalLimits, PlanLimit.WORKSPACES_PER_PROFILE),
                raised(globalLimits, PlanLimit.GROUPS_PER_WORKSPACE),
                raised(globalLimits, PlanLimit.SUBGROUPS_PER_GROUP),
                raised(globalLimits, PlanLimit.LINKS_PER_WORKSPACE),
                raised(globalLimits, PlanLimit.CLOSED_TABS),
                raised(globalLimits, PlanLimit.BROWSING_HISTORY),
                raised(globalLimits, PlanLimit.DEVICES));
    }

    /**
     * Returns the higher of the global value of one limit and its override.
     *
     * @param globalLimits the global Fair Use limits
     * @param limit        the limit
     * @return the global value when there is no override, otherwise the higher of the two
     */
    private int raised(final PlanLimitValues globalLimits, final PlanLimit limit) {
        final int globalMaximum = globalLimits.maximumFor(limit);
        final Integer override = overrideFor(limit);
        return override == null ? globalMaximum : Math.max(globalMaximum, override);
    }
}
