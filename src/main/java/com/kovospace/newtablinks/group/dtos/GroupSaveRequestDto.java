package com.kovospace.newtablinks.group.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body accepted when creating or replacing a group.
 *
 * @param environmentId identifier of the environment the group is displayed in
 * @param name          title shown on the group header
 * @param description   free text describing the group, may be {@code null}
 * @since 0.0.1
 */
@Schema(description = "Body accepted when creating or replacing a group")
public record GroupSaveRequestDto(

        @Schema(description = "Identifier of the owning environment")
        @NotNull UUID environmentId,

        @Schema(description = "Title shown on the group header", example = "Documentation")
        @NotBlank @Size(max = 120) String name,

        @Schema(description = "Free text describing the group", example = "Reference material")
        @Size(max = 500) String description) {
}
