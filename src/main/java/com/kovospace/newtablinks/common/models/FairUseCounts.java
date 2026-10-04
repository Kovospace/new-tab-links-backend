package com.kovospace.newtablinks.common.models;

import java.util.Map;
import java.util.UUID;

/**
 * An account's counts in every collection a refusing Fair Use Policy cap applies to, at one
 * moment.
 *
 * <p>Taken before a sync push and again after it, so that the push is refused only for
 * collections it actually grew; see
 * {@code FairUseLimitGuard#requireBatchDidNotGrowPastCaps}.</p>
 *
 * @param profileCount                 the account's profiles
 * @param workspaceCount               the account's environments, across all profiles
 * @param linkCountsByWorkspaceId      links per environment; an environment without links is
 *                                     absent and counts as zero
 * @since 0.0.16
 */
public record FairUseCounts(
        long profileCount,
        long workspaceCount,
        Map<UUID, Long> linkCountsByWorkspaceId) {

    /**
     * Copies the map, so that a snapshot cannot change after it was taken.
     */
    public FairUseCounts {
        linkCountsByWorkspaceId = Map.copyOf(linkCountsByWorkspaceId);
    }

    /**
     * Returns how many links one environment held.
     *
     * @param workspaceId identifier of the environment
     * @return its link count, zero when it held none or did not exist yet
     */
    public long linkCountOf(final UUID workspaceId) {
        return linkCountsByWorkspaceId.getOrDefault(workspaceId, 0L);
    }
}
