package com.kovospace.newtablinks.group.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A group as returned to a client.
 *
 * @param id            identifier of the group
 * @param environmentId identifier of the environment the group is displayed in
 * @param name          title shown on the group header
 * @param description   free text describing the group, {@code null} when there is none
 * @param position      zero based position among the environment's groups
 * @param createdAt     when the group was created
 * @param updatedAt     when the group was last changed
 * @since 0.0.1
 */
@Schema(description = "A titled box of links inside an environment")
public record GroupDto(
        @Schema(description = "Identifier of the group") UUID id,
        @Schema(description = "Identifier of the owning environment") UUID environmentId,
        @Schema(description = "Title shown on the group header", example = "Documentation") String name,
        @Schema(description = "Free text describing the group", example = "Reference material")
        String description,
        @Schema(description = "Zero based display position", example = "0") int position,
        @Schema(description = "When the group was created") Instant createdAt,
        @Schema(description = "When the group was last changed") Instant updatedAt) {
}
