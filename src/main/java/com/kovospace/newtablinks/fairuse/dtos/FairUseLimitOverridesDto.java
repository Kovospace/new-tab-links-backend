package com.kovospace.newtablinks.fairuse.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/**
 * The Fair Use limits raised for one account - each a number, or {@code null} where the account
 * keeps the global value.
 *
 * <p>On a save, every number must be at least the global Fair Use value of that limit at that
 * moment; the server refuses a lower one with 400, naming the field.</p>
 *
 * @param profiles             raised profile limit, or {@code null}
 * @param workspacesPerProfile raised workspaces-per-profile limit, or {@code null}
 * @param groupsPerWorkspace   raised groups-per-workspace limit, or {@code null}
 * @param subgroupsPerGroup    raised subgroups-per-group limit, or {@code null}
 * @param linksPerWorkspace    raised links-per-workspace limit, or {@code null}
 * @param closedTabs           raised closed-tab history limit, or {@code null}
 * @param browsingHistory      raised browsing history limit, or {@code null}
 * @param devices              raised signed-in installation limit, or {@code null}
 * @since 0.0.20
 */
@Schema(description = "Fair Use limits raised for one account; null = the global value")
public record FairUseLimitOverridesDto(

        @Schema(description = "Raised profile limit", example = "80", nullable = true, minimum = "1")
        @Min(1) Integer profiles,

        @Schema(description = "Raised workspaces-per-profile limit", example = "80",
                nullable = true, minimum = "1")
        @Min(1) Integer workspacesPerProfile,

        @Schema(description = "Raised groups-per-workspace limit", nullable = true, minimum = "1")
        @Min(1) Integer groupsPerWorkspace,

        @Schema(description = "Raised subgroups-per-group limit", nullable = true, minimum = "1")
        @Min(1) Integer subgroupsPerGroup,

        @Schema(description = "Raised links-per-workspace limit", example = "2000",
                nullable = true, minimum = "1")
        @Min(1) Integer linksPerWorkspace,

        @Schema(description = "Raised closed-tab history limit", nullable = true, minimum = "1")
        @Min(1) Integer closedTabs,

        @Schema(description = "Raised browsing history limit", nullable = true, minimum = "1")
        @Min(1) Integer browsingHistory,

        @Schema(description = "Raised signed-in installation limit", nullable = true, minimum = "1")
        @Min(1) Integer devices) {
}
