package com.kovospace.newtablinks.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body accepted when a signed-in user sets or changes their own password.
 *
 * <p>{@code currentPassword} is required only when the account already has one. An account
 * created through Google has no password at all, and this is how its owner gives it one - there
 * is nothing to prove because they are already authenticated.</p>
 *
 * @param currentPassword the existing password; omit for an account that has none
 * @param newPassword     the password to set
 * @since 0.0.3
 */
@Schema(description = "Body accepted when a signed-in user sets or changes their own password")
public record PasswordChangeRequestDto(

        @Schema(description = "The existing password; omit when the account has none yet")
        @Size(max = 200) String currentPassword,

        @Schema(description = "The password to set", example = "a long passphrase is best")
        @NotBlank @Size(min = 10, max = 200) String newPassword) {
}
