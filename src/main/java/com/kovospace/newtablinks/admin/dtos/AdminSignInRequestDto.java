package com.kovospace.newtablinks.admin.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Credentials presented at the operator sign-in.
 *
 * <p>No length or shape constraints beyond being present. What is acceptable here is whatever the
 * deployment configured, and echoing a rule about it would describe the secret.</p>
 *
 * @param username name the operator signs in with
 * @param password password the operator signs in with
 * @since 0.0.6
 */
@Schema(description = "Administrator credentials")
public record AdminSignInRequestDto(

        @Schema(description = "Name the operator signs in with")
        @NotBlank String username,

        @Schema(description = "Password the operator signs in with")
        @NotBlank String password) {
}
