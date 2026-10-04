package com.kovospace.newtablinks.user.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

/**
 * What an installation holds, reported so that the account's devices page can show what
 * synchronises - including data the server never sees because it stays local only.
 *
 * @param profiles every profile the installation holds, with its workspaces
 * @since 0.0.18
 */
@Schema(description = "What an installation holds: its profiles and workspaces, names, sync "
        + "states and counts only")
public record DeviceInventoryReportRequestDto(

        @Schema(description = "Every profile the installation holds, in its order")
        @NotNull @Size(max = DeviceInventoryBounds.MAXIMUM_PROFILES)
        List<@NotNull @Valid DeviceInventoryProfileDto> profiles) {

    /**
     * Tells whether the report lists no more workspaces in total than a report may.
     *
     * @return {@code true} when within {@link DeviceInventoryBounds#MAXIMUM_WORKSPACES}, or when
     *         there is nothing to count yet (the per-field constraints report those)
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "profiles must list at most "
            + DeviceInventoryBounds.MAXIMUM_WORKSPACES + " workspaces in total")
    public boolean isWithinTotalWorkspaceBound() {
        if (profiles == null) {
            return true;
        }
        final long totalWorkspaces = profiles.stream()
                .filter(Objects::nonNull)
                .map(DeviceInventoryProfileDto::workspaces)
                .filter(Objects::nonNull)
                .mapToLong(List::size)
                .sum();
        return totalWorkspaces <= DeviceInventoryBounds.MAXIMUM_WORKSPACES;
    }
}
