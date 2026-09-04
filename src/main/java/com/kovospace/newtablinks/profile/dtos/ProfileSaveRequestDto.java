package com.kovospace.newtablinks.profile.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body accepted when creating or replacing a profile.
 *
 * <p>The owner is deliberately absent: it is taken from the access token, because an owner
 * supplied in the body would be a request to act as whoever the caller names.</p>
 *
 * @param name name shown on the profile switcher
 * @since 0.0.6
 */
@Schema(description = "Body accepted when creating or replacing a profile")
public record ProfileSaveRequestDto(

        @Schema(description = "Name shown on the profile switcher", example = "Default")
        @NotBlank @Size(max = 120) String name) {
}
