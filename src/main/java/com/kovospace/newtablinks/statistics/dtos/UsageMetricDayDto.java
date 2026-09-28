package com.kovospace.newtablinks.statistics.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * One day's total of a usage metric.
 *
 * @param day   the day
 * @param value its total; zero for a day nothing was counted on
 * @since 0.0.11
 */
@Schema(description = "One day's total of a usage metric")
public record UsageMetricDayDto(

        @Schema(description = "The day", example = "2026-09-01")
        LocalDate day,

        @Schema(description = "The day's total; 0 when nothing was counted", example = "42")
        long value) {
}
