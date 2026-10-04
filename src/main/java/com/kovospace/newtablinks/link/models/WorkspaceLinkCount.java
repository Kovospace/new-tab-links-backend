package com.kovospace.newtablinks.link.models;

import java.util.UUID;

/**
 * How many links one environment (workspace) holds, as counted by a single grouped query.
 *
 * @param environmentId identifier of the environment
 * @param linkCount     how many links it holds, through every group and subgroup of it
 * @since 0.0.16
 */
public record WorkspaceLinkCount(UUID environmentId, long linkCount) {
}
