package com.kovospace.newtablinks.subgroup.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body accepted when creating or replacing a subgroup.
 *
 * @param parentGroupId identifier of the group the subgroup is nested in
 * @param name          title shown on the subgroup header
 * @param collapsed     whether the subgroup is folded away
 * @since 0.0.1
 */
@Schema(description = "Body accepted when creating or replacing a subgroup")
public record SubgroupSaveRequestDto(

        @Schema(description = "Identifier of the owning group")
        @NotNull UUID parentGroupId,

        @Schema(description = "Title shown on the subgroup header", example = "Internal")
        @NotBlank @Size(max = 120) String name,

        @Schema(description = "Whether the subgroup is folded away", example = "false")
        boolean collapsed) {
}
