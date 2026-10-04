package com.kovospace.newtablinks.common.models;

/**
 * One full set of plan limits - every {@link PlanLimit} with its maximum.
 *
 * <p>The premium set is bound from configuration as it is
 * ({@code newtablinks.plan-limits.premium.*}); the free set is the premium one with the free
 * plan's own lower numbers laid over it ({@link FreePlanLimitValues#overlayOn(PlanLimitValues)}),
 * because where the free plan names no limit of its own the Fair Use Policy's applies.</p>
 *
 * @param profiles             most profiles an account may synchronise
 * @param workspacesPerProfile most workspaces one profile may synchronise
 * @param groupsPerWorkspace   most groups one workspace may hold
 * @param subgroupsPerGroup    most subgroups one group may hold
 * @param linksPerWorkspace    most links one workspace may hold
 * @param closedTabs           most closed-tab history entries an account keeps
 * @param devices              most extension installations signed in at once
 * @since 0.0.18
 */
public record PlanLimitValues(
        int profiles,
        int workspacesPerProfile,
        int groupsPerWorkspace,
        int subgroupsPerGroup,
        int linksPerWorkspace,
        int closedTabs,
        int devices) {

    /**
     * Rejects a limit that could not work, at startup rather than on the first refused write.
     *
     * <p>A limit below one would refuse the very first record of its kind, which no plan
     * intends; it is far more likely to be a missing or mistyped variable.</p>
     *
     * @throws IllegalArgumentException when any limit is below one
     */
    public PlanLimitValues {
        requirePositive(profiles, PlanLimit.PROFILES);
        requirePositive(workspacesPerProfile, PlanLimit.WORKSPACES_PER_PROFILE);
        requirePositive(groupsPerWorkspace, PlanLimit.GROUPS_PER_WORKSPACE);
        requirePositive(subgroupsPerGroup, PlanLimit.SUBGROUPS_PER_GROUP);
        requirePositive(linksPerWorkspace, PlanLimit.LINKS_PER_WORKSPACE);
        requirePositive(closedTabs, PlanLimit.CLOSED_TABS);
        requirePositive(devices, PlanLimit.DEVICES);
    }

    /**
     * Returns the maximum of one limit.
     *
     * @param limit the limit
     * @return the most records of that kind this set allows
     */
    public int maximumFor(final PlanLimit limit) {
        return switch (limit) {
            case PROFILES -> profiles;
            case WORKSPACES_PER_PROFILE -> workspacesPerProfile;
            case GROUPS_PER_WORKSPACE -> groupsPerWorkspace;
            case SUBGROUPS_PER_GROUP -> subgroupsPerGroup;
            case LINKS_PER_WORKSPACE -> linksPerWorkspace;
            case CLOSED_TABS -> closedTabs;
            case DEVICES -> devices;
        };
    }

    /**
     * Fails when a configured maximum is not positive.
     *
     * @param maximum the configured value
     * @param limit   which limit it is, for the message
     * @throws IllegalArgumentException when {@code maximum} is below one
     */
    static void requirePositive(final int maximum, final PlanLimit limit) {
        if (maximum <= 0) {
            throw new IllegalArgumentException("Plan limit " + limit + " must be positive");
        }
    }
}
