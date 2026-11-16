package com.kovospace.newtablinks.fairuse.dtos;

import com.kovospace.newtablinks.user.dtos.PlanLimitValuesDto;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One account's Fair Use limits as the operator sees them: what was raised, why, and what the
 * account gets now.
 *
 * @param overrides the raised limits; every field {@code null} when the account has no override
 * @param note      the operator's reason, or {@code null}
 * @param fairUse   the global Fair Use limits
 * @param effective the limits that hold for the account right now - its standing, the free
 *                  plan's numbers when it is not premium, and the overrides taken into account
 * @param premium   whether the account is premium right now
 * @since 0.0.20
 */
@Schema(description = "One account's Fair Use overrides, the global limits and what it gets now")
public record UserFairUseLimitsDto(

        @Schema(description = "The raised limits; all null when the account has no override")
        FairUseLimitOverridesDto overrides,

        @Schema(description = "The operator's reason for the raise", nullable = true)
        String note,

        @Schema(description = "The global Fair Use limits")
        PlanLimitValuesDto fairUse,

        @Schema(description = "The limits that hold for the account right now")
        PlanLimitValuesDto effective,

        @Schema(description = "Whether the account is premium right now", example = "true")
        boolean premium) {
}
