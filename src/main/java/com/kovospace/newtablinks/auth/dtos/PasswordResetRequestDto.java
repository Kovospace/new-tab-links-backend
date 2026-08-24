package com.kovospace.newtablinks.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body accepted when asking for a password reset link.
 *
 * @param email address to send the link to
 * @since 0.0.3
 */
@Schema(description = "Body accepted when asking for a password reset link")
public record PasswordResetRequestDto(

        @Schema(description = "Address to send the link to", example = "someone@example.com")
        @NotBlank @Email @Size(max = 320) String email) {
}
