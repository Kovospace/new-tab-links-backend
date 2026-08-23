package com.kovospace.newtablinks.environment.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body accepted when creating or replacing an environment.
 *
 * @param ownerId identifier of the user the environment belongs to
 * @param name    name shown on the environment switcher
 * @since 0.0.1
 */
@Schema(description = "Body accepted when creating or replacing an environment")
public record EnvironmentSaveRequestDto(

        @Schema(description = "Identifier of the owning user")
        @NotNull UUID ownerId,

        @Schema(description = "Name shown on the environment switcher", example = "Work")
        @NotBlank @Size(max = 120) String name) {
}
