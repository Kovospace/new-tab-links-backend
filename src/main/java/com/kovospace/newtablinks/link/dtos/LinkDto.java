package com.kovospace.newtablinks.link.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A link as returned to a client.
 *
 * @param id               identifier of the link
 * @param parentGroupId    identifier of the group the link belongs to
 * @param parentSubgroupId identifier of the subgroup the link is nested in, {@code null} when none
 * @param title            text shown for the link
 * @param url              address the link points to
 * @param faviconUrl       address of the cached favicon, {@code null} when none
 * @param position         zero based position among the links of the same parent
 * @param createdAt        when the link was created
 * @param updatedAt        when the link was last changed
 * @since 0.0.1
 */
@Schema(description = "A single bookmark rendered on the new tab page")
public record LinkDto(
        @Schema(description = "Identifier of the link") UUID id,
        @Schema(description = "Identifier of the owning group") UUID parentGroupId,
        @Schema(description = "Identifier of the owning subgroup, null when the link sits directly in the group")
        UUID parentSubgroupId,
        @Schema(description = "Text shown for the link", example = "Spring Boot reference") String title,
        @Schema(description = "Address the link points to", example = "https://spring.io") String url,
        @Schema(description = "Address of the cached favicon") String faviconUrl,
        @Schema(description = "Zero based display position", example = "0") int position,
        @Schema(description = "When the link was created") Instant createdAt,
        @Schema(description = "When the link was last changed") Instant updatedAt) {
}
