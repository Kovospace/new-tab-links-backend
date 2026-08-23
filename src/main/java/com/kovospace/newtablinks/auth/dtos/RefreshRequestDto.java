package com.kovospace.newtablinks.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Body accepted when trading a refresh token for a fresh pair.
 *
 * @param refreshToken the refresh token previously issued to this client
 * @since 0.0.2
 */
@Schema(description = "Body accepted when refreshing an expired access token")
public record RefreshRequestDto(

        @Schema(description = "The refresh token previously issued to this client")
        @NotBlank String refreshToken) {
}
