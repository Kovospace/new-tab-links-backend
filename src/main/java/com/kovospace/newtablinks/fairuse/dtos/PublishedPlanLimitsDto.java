package com.kovospace.newtablinks.fairuse.dtos;

import com.kovospace.newtablinks.user.dtos.PlanLimitValuesDto;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The plans' limits as published to anybody - what the website's price table and Fair Use page
 * show.
 *
 * @param free    what a free account gets: the free plan's own numbers over the global Fair Use
 *                limits
 * @param fairUse the global Fair Use limits - what a premium account gets
 * @since 0.0.20
 */
@Schema(description = "The free plan's and the Fair Use Policy's limits, as published")
public record PublishedPlanLimitsDto(

        @Schema(description = "What a free account gets")
        PlanLimitValuesDto free,

        @Schema(description = "The global Fair Use limits - what a premium account gets")
        PlanLimitValuesDto fairUse) {
}
