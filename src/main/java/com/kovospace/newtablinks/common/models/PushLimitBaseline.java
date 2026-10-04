package com.kovospace.newtablinks.common.models;

import java.util.Map;
import java.util.UUID;

/**
 * What a sync push is judged against: the account's limits and container sizes before its first
 * operation.
 *
 * <p>Taken once under the account's row lock, so the push is judged against one standing even if
 * the entitlement changes while it runs, and refused only for containers it actually grew.</p>
 *
 * @param limits                     the limits that hold for the account
 * @param groupCountsByWorkspaceId   groups per environment; an absent one counts as zero
 * @param subgroupCountsByGroupId    subgroups per group; an absent one counts as zero
 * @param linkCountsByWorkspaceId    links per environment; an absent one counts as zero
 * @param closedTabCount             the account's closed-tab history entries
 * @since 0.0.18
 */
public record PushLimitBaseline(
        EffectivePlanLimits limits,
        Map<UUID, Long> groupCountsByWorkspaceId,
        Map<UUID, Long> subgroupCountsByGroupId,
        Map<UUID, Long> linkCountsByWorkspaceId,
        long closedTabCount) {

    /**
     * Copies the maps, so that a baseline cannot change after it was taken.
     */
    public PushLimitBaseline {
        groupCountsByWorkspaceId = Map.copyOf(groupCountsByWorkspaceId);
        subgroupCountsByGroupId = Map.copyOf(subgroupCountsByGroupId);
        linkCountsByWorkspaceId = Map.copyOf(linkCountsByWorkspaceId);
    }
}
