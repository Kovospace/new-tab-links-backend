package com.kovospace.newtablinks.user.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Every plan limit with the maximum that holds for the account.
 *
 * @param profiles             most profiles that synchronise
 * @param workspacesPerProfile most workspaces of one profile that synchronise
 * @param groupsPerWorkspace   most groups one workspace may hold
 * @param subgroupsPerGroup    most subgroups one group may hold
 * @param linksPerWorkspace    most links one workspace may hold
 * @param closedTabs           most closed-tab history entries kept
 * @param devices              most extension installations signed in at once
 * @since 0.0.18
 */
@Schema(description = "Every plan limit with the maximum that holds for the account")
public record PlanLimitValuesDto(

        @Schema(description = "Most profiles that synchronise - the first ones the server "
                + "stored", example = "1")
        int profiles,

        @Schema(description = "Most workspaces of one profile that synchronise - the first ones "
                + "the server stored", example = "2")
        int workspacesPerProfile,

        @Schema(description = "Most groups one workspace may hold", example = "25")
        int groupsPerWorkspace,

        @Schema(description = "Most subgroups one group may hold", example = "25")
        int subgroupsPerGroup,

        @Schema(description = "Most links one workspace may hold", example = "500")
        int linksPerWorkspace,

        @Schema(description = "Most closed-tab history entries kept; the oldest drop off",
                example = "25")
        int closedTabs,

        @Schema(description = "Most extension installations signed in at once", example = "5")
        int devices) {
}
