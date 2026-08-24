package com.kovospace.newtablinks.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body accepted when signing in with a password, as the browser extension does.
 *
 * @param usernameOrEmail whichever of the two the user typed
 * @param password        the password, in plaintext over TLS
 * @since 0.0.2
 */
@Schema(description = "Body accepted when signing in with a password")
public record LoginRequestDto(

        @Schema(description = "Username or email address", example = "kovo")
        @NotBlank @Size(max = 320) String usernameOrEmail,

        @Schema(description = "The password")
        @NotBlank @Size(max = 200) String password) {
}
