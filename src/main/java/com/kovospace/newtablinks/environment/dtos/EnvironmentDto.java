package com.kovospace.newtablinks.environment.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * An environment as returned to a client.
 *
 * @param id        identifier of the environment
 * @param ownerId   identifier of the user the environment belongs to
 * @param name      name shown on the environment switcher
 * @param position  zero based position among the owner's environments
 * @param createdAt when the environment was created
 * @param updatedAt when the environment was last changed
 * @since 0.0.1
 */
@Schema(description = "A workspace grouping the user's link groups")
public record EnvironmentDto(
        @Schema(description = "Identifier of the environment") UUID id,
        @Schema(description = "Identifier of the owning user") UUID ownerId,
        @Schema(description = "Name shown on the environment switcher", example = "Work") String name,
        @Schema(description = "Zero based display position", example = "0") int position,
        @Schema(description = "When the environment was created") Instant createdAt,
        @Schema(description = "When the environment was last changed") Instant updatedAt) {
}
