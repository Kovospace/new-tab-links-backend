package com.kovospace.newtablinks.fairuse.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The global Fair Use limits the operator wants in force - every limit required, each at least one.
 *
 * <p>The same eight fields as {@code PlanLimitValuesDto}, as boxed integers so that a missing one
 * is reported as a rejected field rather than as an unreadable body.</p>
 *
 * @param profiles             most profiles an account may synchronise
 * @param workspacesPerProfile most workspaces one profile may synchronise
 * @param groupsPerWorkspace   most groups one workspace may hold
 * @param subgroupsPerGroup    most subgroups one group may hold
 * @param linksPerWorkspace    most links one workspace may hold
 * @param closedTabs           most closed-tab history entries an account keeps
 * @param browsingHistory      most browsing history entries an account keeps
 * @param devices              most extension installations signed in at once
 * @since 0.0.20
 */
@Schema(description = "The global Fair Use limits to put in force; every field required, each at least 1")
public record FairUseLimitsSaveRequestDto(

        @Schema(description = "Most profiles that synchronise", example = "50",
                requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        @NotNull @Min(1) Integer profiles,

        @Schema(description = "Most workspaces of one profile that synchronise", example = "50",
                requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        @NotNull @Min(1) Integer workspacesPerProfile,

        @Schema(description = "Most groups one workspace may hold", example = "25",
                requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        @NotNull @Min(1) Integer groupsPerWorkspace,

        @Schema(description = "Most subgroups one group may hold", example = "25",
                requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        @NotNull @Min(1) Integer subgroupsPerGroup,

        @Schema(description = "Most links one workspace may hold", example = "500",
                requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        @NotNull @Min(1) Integer linksPerWorkspace,

        @Schema(description = "Most closed-tab history entries kept", example = "500",
                requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        @NotNull @Min(1) Integer closedTabs,

        @Schema(description = "Most browsing history entries kept", example = "1000",
                requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        @NotNull @Min(1) Integer browsingHistory,

        @Schema(description = "Most extension installations signed in at once", example = "100",
                requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        @NotNull @Min(1) Integer devices) {
}
