package com.kovospace.newtablinks.environment.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body accepted when creating or replacing an environment.
 *
 * <p>The owner is deliberately absent: it is taken from the access token, because an owner
 * supplied in the body would be a request to act as whoever the caller names.</p>
 *
 * @param name name shown on the environment switcher
 * @since 0.0.1
 */
@Schema(description = "Body accepted when creating or replacing an environment")
public record EnvironmentSaveRequestDto(

        @Schema(description = "Name shown on the environment switcher", example = "Work")
        @NotBlank @Size(max = 120) String name) {
}
