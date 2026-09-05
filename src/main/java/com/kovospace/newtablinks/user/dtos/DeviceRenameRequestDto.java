package com.kovospace.newtablinks.user.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new label for a device.
 *
 * <p>Only the label. A device is identified by the installation that reported it, so renaming one
 * cannot merge it with another or split it in two - which is exactly why the name is safe to let
 * a user choose freely, duplicates included.</p>
 *
 * @param name what to call the device from now on
 * @since 0.0.7
 */
@Schema(description = "A new label for a device")
public record DeviceRenameRequestDto(

        @Schema(description = "What to call the device", example = "Work laptop, Firefox")
        @NotBlank @Size(max = 120) String name) {
}
