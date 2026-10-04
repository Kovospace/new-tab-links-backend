package com.kovospace.newtablinks.user.dtos;

import com.kovospace.newtablinks.user.models.InventorySyncState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * One workspace an installation holds, as it reported it - names and counts only, never a link,
 * an address or a group name.
 *
 * @param accountId     identifier of the workspace in the account, {@code null} when it exists
 *                      on the installation only
 * @param name          the workspace's name
 * @param syncState     whether it synchronises, as the installation decided
 * @param groupCount    how many groups it holds
 * @param subgroupCount how many subgroups its groups hold
 * @param linkCount     how many links it holds, through every group and subgroup
 * @since 0.0.18
 */
@Schema(description = "A workspace on an installation, as the installation reported it")
public record DeviceInventoryWorkspaceDto(

        @Schema(description = "Identifier of the workspace in the account; null when it exists "
                + "on the installation only", nullable = true)
        UUID accountId,

        @Schema(description = "The workspace's name", example = "Work")
        @NotBlank @Size(max = DeviceInventoryBounds.MAXIMUM_NAME_LENGTH) String name,

        @Schema(description = "Whether it synchronises", example = "LOCAL_ONLY_FREE_LIMIT")
        @NotNull InventorySyncState syncState,

        @Schema(description = "How many groups it holds", example = "4")
        @PositiveOrZero @Max(DeviceInventoryBounds.MAXIMUM_COUNT) int groupCount,

        @Schema(description = "How many subgroups its groups hold", example = "2")
        @PositiveOrZero @Max(DeviceInventoryBounds.MAXIMUM_COUNT) int subgroupCount,

        @Schema(description = "How many links it holds", example = "37")
        @PositiveOrZero @Max(DeviceInventoryBounds.MAXIMUM_COUNT) int linkCount) {
}
