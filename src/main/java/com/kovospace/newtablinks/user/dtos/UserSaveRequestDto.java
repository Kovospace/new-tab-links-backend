package com.kovospace.newtablinks.user.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body accepted when creating or replacing a user.
 *
 * @param email       address identifying the user, must be unique across the service
 * @param displayName name shown in the user interface
 * @since 0.0.1
 */
@Schema(description = "Body accepted when creating or replacing a user")
public record UserSaveRequestDto(

        @Schema(description = "Address identifying the user", example = "someone@example.com")
        @NotBlank @Email @Size(max = 320) String email,

        @Schema(description = "Name shown in the user interface", example = "Matej")
        @NotBlank @Size(max = 120) String displayName) {
}
