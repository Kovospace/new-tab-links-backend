package com.kovospace.newtablinks.user.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A user as returned to a client.
 *
 * @param id          identifier of the user
 * @param email       address identifying the user
 * @param displayName name shown in the user interface
 * @param createdAt   when the user was created
 * @param updatedAt   when the user was last changed
 * @since 0.0.1
 */
@Schema(description = "A user of the service")
public record UserDto(
        @Schema(description = "Identifier of the user") UUID id,
        @Schema(description = "Address identifying the user", example = "someone@example.com") String email,
        @Schema(description = "Name shown in the user interface", example = "Matej") String displayName,
        @Schema(description = "When the user was created") Instant createdAt,
        @Schema(description = "When the user was last changed") Instant updatedAt) {
}
