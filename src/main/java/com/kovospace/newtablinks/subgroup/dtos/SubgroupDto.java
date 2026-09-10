package com.kovospace.newtablinks.subgroup.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A subgroup as returned to a client.
 *
 * @param id            identifier of the subgroup
 * @param parentGroupId identifier of the group the subgroup is nested in
 * @param name             title shown on the subgroup header
 * @param description      free text describing the subgroup, {@code null} when there is none
 * @param position         zero based position among the group's subgroups
 * @param collapsed        whether the subgroup is folded away right now
 * @param defaultCollapsed whether the subgroup starts folded away on a freshly opened page
 * @param catchLinksIntoTabGroup whether tabs navigating to the subgroup's links are pulled into
 *                               its browser tab group
 * @param color            name of the Chrome tab group colour the subgroup is painted with,
 *                         {@code null} when it has never been given one
 * @param createdAt     when the subgroup was created
 * @param updatedAt     when the subgroup was last changed
 * @since 0.0.1
 */
@Schema(description = "A collapsible section inside a group")
public record SubgroupDto(
        @Schema(description = "Identifier of the subgroup") UUID id,
        @Schema(description = "Identifier of the owning group") UUID parentGroupId,
        @Schema(description = "Title shown on the subgroup header", example = "Internal") String name,
        @Schema(description = "Free text describing the subgroup", example = "Only reachable on the VPN")
        String description,
        @Schema(description = "Zero based display position", example = "0") int position,
        @Schema(description = "Whether the subgroup is folded away right now", example = "false")
        boolean collapsed,
        @Schema(description = "Whether the subgroup starts folded away on a freshly opened page",
                example = "false")
        boolean defaultCollapsed,
        @Schema(description = "Whether tabs navigating to the subgroup's links are pulled into its "
                + "browser tab group", example = "false")
        boolean catchLinksIntoTabGroup,
        @Schema(description = "Name of the Chrome tab group colour the subgroup is painted with, "
                + "null when it has never been given one", example = "cyan")
        String color,
        @Schema(description = "When the subgroup was created") Instant createdAt,
        @Schema(description = "When the subgroup was last changed") Instant updatedAt) {
}
