package com.kovospace.newtablinks.statistics.dtos;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One usage metric over one calendar month, ready to draw as a graph.
 *
 * @param metric the metric's identifier
 * @param month  the month, as {@code YYYY-MM}
 * @param days   every day of the month in order, with no gaps
 * @param total  the sum of {@code days}
 * @since 0.0.11
 */
@Schema(description = "One usage metric over one calendar month")
public record UsageMetricMonthDto(

        @Schema(description = "The metric", example = "new_tabs",
                allowableValues = {"new_tabs", "website_visitors"})
        String metric,

        @Schema(description = "The month", example = "2026-09")
        String month,

        @ArraySchema(
                arraySchema = @Schema(description = "Every day of the month in order, including "
                        + "days with nothing counted, so the graph needs no gap handling"),
                schema = @Schema(implementation = UsageMetricDayDto.class))
        List<UsageMetricDayDto> days,

        @Schema(description = "Sum of the month's days", example = "1234")
        long total) {

    /**
     * Keeps the list of days unmodifiable, whatever the caller passed.
     */
    public UsageMetricMonthDto {
        days = List.copyOf(days);
    }
}
