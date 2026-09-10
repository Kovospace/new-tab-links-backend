package com.kovospace.newtablinks.subgroup.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body accepted when creating or replacing a subgroup.
 *
 * @param parentGroupId identifier of the group the subgroup is nested in
 * @param name             title shown on the subgroup header
 * @param description      free text describing the subgroup, may be {@code null}
 * @param collapsed        whether the subgroup is folded away right now
 * @param defaultCollapsed whether the subgroup starts folded away on a freshly opened page
 * @param catchLinksIntoTabGroup whether tabs navigating to the subgroup's links are pulled into
 *                               its browser tab group
 * @param color            name of the Chrome tab group colour to paint the subgroup with, may be
 *                         {@code null} to leave it without one
 * @since 0.0.1
 */
@Schema(description = "Body accepted when creating or replacing a subgroup")
public record SubgroupSaveRequestDto(

        @Schema(description = "Identifier of the owning group")
        @NotNull UUID parentGroupId,

        @Schema(description = "Title shown on the subgroup header", example = "Internal")
        @NotBlank @Size(max = 120) String name,

        @Schema(description = "Free text describing the subgroup", example = "Only reachable on the VPN")
        @Size(max = 500) String description,

        @Schema(description = "Whether the subgroup is folded away right now", example = "false")
        boolean collapsed,

        @Schema(description = "Whether the subgroup starts folded away on a freshly opened page",
                example = "false")
        boolean defaultCollapsed,

        @Schema(description = "Whether tabs navigating to the subgroup's links are pulled into its "
                + "browser tab group", example = "false")
        boolean catchLinksIntoTabGroup,

        @Schema(description = "Name of the Chrome tab group colour to paint the subgroup with, "
                + "one of the names Chrome accepts; null leaves the subgroup without one",
                example = "cyan")
        @Size(max = 16) String color) {
}
