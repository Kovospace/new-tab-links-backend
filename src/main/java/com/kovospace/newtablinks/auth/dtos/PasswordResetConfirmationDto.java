package com.kovospace.newtablinks.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body accepted when setting a new password from a reset link.
 *
 * @param token       the token taken from the reset link
 * @param newPassword the password to set
 * @since 0.0.3
 */
@Schema(description = "Body accepted when setting a new password from a reset link")
public record PasswordResetConfirmationDto(

        @Schema(description = "The token taken from the reset link")
        @NotBlank String token,

        @Schema(description = "The password to set", example = "a long passphrase is best")
        @NotBlank @Size(min = 10, max = 200) String newPassword) {
}
