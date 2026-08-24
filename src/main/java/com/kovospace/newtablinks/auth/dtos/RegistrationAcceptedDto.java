package com.kovospace.newtablinks.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Deliberately uninformative answer to a registration attempt.
 *
 * <p>The same body is returned whether an account was created or the address was already taken,
 * so that registration cannot be used to discover who is registered. The difference is in what
 * gets mailed to the address, which only its owner can read.</p>
 *
 * @param message wording to show the user
 * @since 0.0.2
 */
@Schema(description = "Uniform answer to a registration attempt")
public record RegistrationAcceptedDto(

        @Schema(description = "Wording to show the user",
                example = "If that address can receive mail, an activation link is on its way.")
        String message) {
}
