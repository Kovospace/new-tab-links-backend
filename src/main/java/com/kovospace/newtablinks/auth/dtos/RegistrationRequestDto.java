package com.kovospace.newtablinks.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body accepted when registering an account the classic way, from the website.
 *
 * @param username    name the user will sign in with
 * @param email       address the activation link is sent to
 * @param password    chosen password, in plaintext over TLS and never stored as given
 * @param displayName name shown in the user interface
 * @since 0.0.2
 */
@Schema(description = "Body accepted when registering an account with a password")
public record RegistrationRequestDto(

        @Schema(description = "Name the user will sign in with", example = "kovo")
        @NotBlank
        @Size(min = 3, max = 60)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "may contain only letters, digits, dot, underscore and hyphen")
        String username,

        @Schema(description = "Address the activation link is sent to", example = "someone@example.com")
        @NotBlank @Email @Size(max = 320) String email,

        @Schema(description = "Chosen password", example = "a long passphrase is best")
        @NotBlank @Size(min = 10, max = 200) String password,

        @Schema(description = "Name shown in the user interface", example = "Matej")
        @NotBlank @Size(max = 120) String displayName) {
}
