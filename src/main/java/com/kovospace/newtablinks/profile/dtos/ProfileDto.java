package com.kovospace.newtablinks.profile.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A profile as returned to a client.
 *
 * @param id        identifier of the profile
 * @param ownerId   identifier of the user the profile belongs to
 * @param name      name shown on the profile switcher
 * @param position  zero based position among the owner's profiles
 * @param enableDragAndDrop whether the profile lets its links and groups be rearranged by dragging
 * @param createdAt when the profile was created
 * @param updatedAt when the profile was last changed
 * @since 0.0.6
 */
@Schema(description = "A named set of environments, the top of the link hierarchy")
public record ProfileDto(
        @Schema(description = "Identifier of the profile") UUID id,
        @Schema(description = "Identifier of the owning user") UUID ownerId,
        @Schema(description = "Name shown on the profile switcher", example = "Default") String name,
        @Schema(description = "Zero based display position", example = "0") int position,
        @Schema(description = "Whether the profile lets its links and groups be rearranged by "
                + "dragging", example = "false")
        boolean enableDragAndDrop,
        @Schema(description = "When the profile was created") Instant createdAt,
        @Schema(description = "When the profile was last changed") Instant updatedAt) {
}
