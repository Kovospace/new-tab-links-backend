package com.kovospace.newtablinks.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A freshly minted connect code, for the website to show to a signed-in user.
 *
 * @param code      the code to display, already grouped for legibility
 * @param expiresAt moment the code stops working
 * @since 0.0.2
 */
@Schema(description = "A connect code for the user to type into the browser extension")
public record ExtensionConnectCodeDto(

        @Schema(description = "The code to display", example = "4F2K-9QX1")
        String code,

        @Schema(description = "Moment the code stops working")
        Instant expiresAt) {
}
