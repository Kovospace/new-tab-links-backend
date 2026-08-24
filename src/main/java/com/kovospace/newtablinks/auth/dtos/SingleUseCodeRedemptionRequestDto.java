package com.kovospace.newtablinks.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body accepted when exchanging a single use code for tokens.
 *
 * <p>Serves both exchanges: the website trading its sign-in handoff code, and the extension
 * trading the connect code the user retyped.</p>
 *
 * @param code the code as it was received or typed; punctuation and case do not matter
 * @since 0.0.2
 */
@Schema(description = "Body accepted when exchanging a single use code for tokens")
public record SingleUseCodeRedemptionRequestDto(

        @Schema(description = "The code as received or typed", example = "4F2K-9QX1")
        @NotBlank @Size(max = 200) String code) {
}
