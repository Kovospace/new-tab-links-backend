package com.kovospace.newtablinks.environment.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body accepted when creating or replacing an environment.
 *
 * <p>The owner is deliberately absent: it is taken from the access token, because an owner
 * supplied in the body would be a request to act as whoever the caller names.</p>
 *
 * @param profileId   identifier of the profile the environment is filed under
 * @param name        name shown on the environment switcher
 * @param description free text describing the environment, may be {@code null}
 * @since 0.0.1
 */
@Schema(description = "Body accepted when creating or replacing an environment")
public record EnvironmentSaveRequestDto(

        @Schema(description = "Identifier of the profile the environment is filed under")
        @NotNull UUID profileId,

        @Schema(description = "Name shown on the environment switcher", example = "Work")
        @NotBlank @Size(max = 120) String name,

        @Schema(description = "Free text describing the environment", example = "Everything work related")
        @Size(max = 500) String description) {
}
