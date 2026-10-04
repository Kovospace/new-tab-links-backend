package com.kovospace.newtablinks.user.dtos;

import com.kovospace.newtablinks.user.models.InventorySyncState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * One profile an installation holds, with its workspaces, as it reported it.
 *
 * @param accountId  identifier of the profile in the account, {@code null} when it exists on the
 *                   installation only
 * @param name       the profile's name
 * @param syncState  whether it synchronises, as the installation decided
 * @param workspaces its workspaces, in the installation's order
 * @since 0.0.18
 */
@Schema(description = "A profile on an installation, with its workspaces")
public record DeviceInventoryProfileDto(

        @Schema(description = "Identifier of the profile in the account; null when it exists on "
                + "the installation only", nullable = true)
        UUID accountId,

        @Schema(description = "The profile's name", example = "Default")
        @NotBlank @Size(max = DeviceInventoryBounds.MAXIMUM_NAME_LENGTH) String name,

        @Schema(description = "Whether it synchronises", example = "SYNCHRONISED")
        @NotNull InventorySyncState syncState,

        @Schema(description = "Its workspaces, in the installation's order")
        @NotNull @Size(max = DeviceInventoryBounds.MAXIMUM_WORKSPACES_PER_PROFILE)
        List<@NotNull @Valid DeviceInventoryWorkspaceDto> workspaces) {
}
