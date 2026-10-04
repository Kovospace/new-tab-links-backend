package com.kovospace.newtablinks.common.models;

import java.util.Set;
import java.util.UUID;

/**
 * The profiles and workspaces one sync push wrote into.
 *
 * <p>A profile is written into by an upsert of it; a workspace by an upsert of it, or by any
 * upsert or deletion of a group, subgroup or link in it. Deleting a workspace writes into its
 * profile. Deleting a profile writes into nothing that remains. Closed tabs are not recorded:
 * their history never refuses.</p>
 *
 * <p>Known gap: a record moved out of a container is recorded only under its new one, so a move
 * out of a workspace without a slot is not refused.</p>
 *
 * @param profileIds   profiles written into, as stored (after remapping)
 * @param workspaceIds workspaces written into, as stored (after remapping)
 * @since 0.0.18
 */
public record PushFootprint(Set<UUID> profileIds, Set<UUID> workspaceIds) {

    /**
     * Copies the sets, so that a footprint cannot change after it was taken.
     */
    public PushFootprint {
        profileIds = Set.copyOf(profileIds);
        workspaceIds = Set.copyOf(workspaceIds);
    }
}
