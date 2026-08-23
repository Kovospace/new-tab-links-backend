package com.kovospace.newtablinks.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * What a client receives once it has proven who it is.
 *
 * <p>The access token is short lived and sent on every request; the refresh token is long lived,
 * stored by the client, and exchanged for a new pair when the access token expires. A browser
 * extension keeps the refresh token in {@code chrome.storage.local} and mints access tokens on
 * demand, because its service worker is killed whenever it goes idle.</p>
 *
 * @param accessToken           signed token to send in the {@code Authorization} header
 * @param accessTokenExpiresInSeconds  remaining lifetime of the access token
 * @param refreshToken          opaque token used to obtain the next pair
 * @param userId                identifier of the authenticated user
 * @param username              name of the authenticated user
 * @since 0.0.2
 */
@Schema(description = "Access and refresh tokens issued to a signed-in client")
public record TokenPairDto(

        @Schema(description = "Signed token for the Authorization header")
        String accessToken,

        @Schema(description = "Remaining lifetime of the access token, in seconds", example = "900")
        long accessTokenExpiresInSeconds,

        @Schema(description = "Opaque token used to obtain the next pair")
        String refreshToken,

        @Schema(description = "Identifier of the authenticated user")
        UUID userId,

        @Schema(description = "Name of the authenticated user", example = "kovo")
        String username) {
}
