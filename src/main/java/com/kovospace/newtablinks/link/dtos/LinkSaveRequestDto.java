package com.kovospace.newtablinks.link.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body accepted when creating or replacing a link.
 *
 * @param parentGroupId    identifier of the group the link belongs to
 * @param parentSubgroupId identifier of the subgroup to nest the link in, {@code null} to keep it
 *                         directly under the group
 * @param title            text shown for the link
 * @param url              address the link points to
 * @param faviconUrl       address of the cached favicon, may be {@code null}
 * @since 0.0.1
 */
@Schema(description = "Body accepted when creating or replacing a link")
public record LinkSaveRequestDto(

        @Schema(description = "Identifier of the owning group")
        @NotNull UUID parentGroupId,

        @Schema(description = "Identifier of the owning subgroup, omit to keep the link directly in the group")
        UUID parentSubgroupId,

        @Schema(description = "Text shown for the link", example = "Spring Boot reference")
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "Address the link points to", example = "https://spring.io")
        @NotBlank @Size(max = 2048) String url,

        @Schema(description = "Address of the cached favicon")
        @Size(max = 2048) String faviconUrl) {
}
