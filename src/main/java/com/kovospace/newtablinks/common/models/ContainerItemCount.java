package com.kovospace.newtablinks.common.models;

import java.util.UUID;

/**
 * How many records of one kind a single container holds - links of a workspace, groups of a
 * workspace, subgroups of a group.
 *
 * <p>A projection, built by JPQL constructor expressions in the repositories that count per
 * container for {@code SyncPushLimitGuard}.</p>
 *
 * @param containerId identifier of the container
 * @param itemCount   how many records it holds
 * @since 0.0.16
 */
public record ContainerItemCount(UUID containerId, long itemCount) {
}
