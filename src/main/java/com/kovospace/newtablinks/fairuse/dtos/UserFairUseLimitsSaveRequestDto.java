package com.kovospace.newtablinks.fairuse.dtos;

import com.kovospace.newtablinks.fairuse.models.UserFairUseOverrideEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * The Fair Use limits the operator raises for one account, with the reason.
 *
 * <p>Replaces whatever the account had: a limit left {@code null} goes back to the global value.
 * With every limit {@code null} - or {@code overrides} absent - the override is removed, exactly
 * as {@code DELETE} would.</p>
 *
 * @param overrides the raised limits; {@code null} is read as none raised
 * @param note      the operator's reason, or {@code null}; a blank one is stored as {@code null}
 * @since 0.0.20
 */
@Schema(description = "Fair Use limits to raise for one account; all null removes the override")
public record UserFairUseLimitsSaveRequestDto(

        @Schema(description = "The raised limits; null fields keep the global value")
        @Valid FairUseLimitOverridesDto overrides,

        @Schema(description = "The operator's reason for the raise", example = "Teacher, one "
                + "workspace per class", nullable = true,
                maxLength = UserFairUseOverrideEntity.NOTE_MAXIMUM_LENGTH)
        @Size(max = UserFairUseOverrideEntity.NOTE_MAXIMUM_LENGTH) String note) {
}
