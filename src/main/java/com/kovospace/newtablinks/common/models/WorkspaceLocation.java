package com.kovospace.newtablinks.common.models;

import java.util.UUID;

/**
 * Where a workspace sits in an account - what a plan-limit check needs to know about it.
 *
 * @param ownerId     identifier of the account
 * @param profileId   identifier of the profile the workspace is filed under
 * @param workspaceId identifier of the workspace (environment)
 * @since 0.0.18
 */
public record WorkspaceLocation(UUID ownerId, UUID profileId, UUID workspaceId) {
}
