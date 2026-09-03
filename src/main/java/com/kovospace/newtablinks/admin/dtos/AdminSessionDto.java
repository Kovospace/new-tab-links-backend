package com.kovospace.newtablinks.admin.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * What a successful operator sign-in returns.
 *
 * <p>One token and its expiry, and deliberately no refresh token: an admin session is meant to be
 * short, and a long-lived credential for this identity is the one thing that should not exist.
 * When it expires the operator signs in again.</p>
 *
 * @param accessToken the token to send as a bearer on every admin request
 * @param expiresAt   moment the token stops working
 * @since 0.0.6
 */
@Schema(description = "A short-lived administrator session")
public record AdminSessionDto(

        @Schema(description = "Bearer token for the endpoints under /api/v1/admin")
        String accessToken,

        @Schema(description = "Moment the token stops working")
        Instant expiresAt) {
}
