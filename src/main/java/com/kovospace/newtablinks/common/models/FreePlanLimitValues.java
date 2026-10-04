package com.kovospace.newtablinks.common.models;

/**
 * The limits the free plan sets lower than the Fair Use Policy - and only those.
 *
 * <p>Groups per workspace, subgroups per group and links per workspace are deliberately absent:
 * the free plan has no number of its own for them, and the premium (Fair Use) number applies.
 * Leaving them out, rather than repeating the premium values, keeps every number in one place.</p>
 *
 * @param profiles             most profiles a free account may synchronise
 * @param workspacesPerProfile most workspaces one profile of a free account may synchronise
 * @param closedTabs           most closed-tab history entries a free account keeps
 * @param devices              most extension installations a free account may have signed in
 * @since 0.0.18
 */
public record FreePlanLimitValues(
        int profiles,
        int workspacesPerProfile,
        int closedTabs,
        int devices) {

    /**
     * Rejects a limit below one, for the reason given on {@link PlanLimitValues}.
     *
     * @throws IllegalArgumentException when any limit is below one
     */
    public FreePlanLimitValues {
        PlanLimitValues.requirePositive(profiles, PlanLimit.PROFILES);
        PlanLimitValues.requirePositive(workspacesPerProfile, PlanLimit.WORKSPACES_PER_PROFILE);
        PlanLimitValues.requirePositive(closedTabs, PlanLimit.CLOSED_TABS);
        PlanLimitValues.requirePositive(devices, PlanLimit.DEVICES);
    }

    /**
     * Builds the free plan's full set: the premium set with the free plan's own numbers in place.
     *
     * @param premiumLimits the premium (Fair Use Policy) set
     * @return the free plan's full set
     */
    public PlanLimitValues overlayOn(final PlanLimitValues premiumLimits) {
        return new PlanLimitValues(
                profiles,
                workspacesPerProfile,
                premiumLimits.groupsPerWorkspace(),
                premiumLimits.subgroupsPerGroup(),
                premiumLimits.linksPerWorkspace(),
                closedTabs,
                devices);
    }
}
