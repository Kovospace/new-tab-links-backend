package com.kovospace.newtablinks.user.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The limits that hold for the account right now, with the standing they follow from.
 *
 * <p>What the extension applies to a signed-in installation instead of its compiled defaults, so
 * that changing a limit needs no extension release.</p>
 *
 * @param premium whether the account is premium right now
 * @param limits  the limits that follow from it
 * @since 0.0.18
 */
@Schema(description = "The limits that hold for the account right now")
public record PlanLimitsDto(

        @Schema(description = "Whether the account is premium right now", example = "false")
        boolean premium,

        @Schema(description = "The limits that follow from it")
        PlanLimitValuesDto limits) {
}
