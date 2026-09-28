package com.kovospace.newtablinks.statistics.dtos;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A batch of new-tab counts the extension has not reported yet.
 *
 * @param days one entry per day with unsent counts; at most {@link #MAXIMUM_DAYS_PER_REPORT}
 * @since 0.0.11
 */
@Schema(description = "New-tab counts not reported yet, one entry per day")
public record NewTabReportRequestDto(

        @ArraySchema(
                arraySchema = @Schema(description = "The unsent days. More than eight is a "
                        + "malformed report; the extension keeps at most seven days plus today."),
                schema = @Schema(implementation = NewTabDayCountDto.class))
        @NotNull
        @Size(max = MAXIMUM_DAYS_PER_REPORT)
        List<@NotNull @Valid NewTabDayCountDto> days) {

    /**
     * Most entries one report may carry: the seven days the extension keeps, plus today.
     */
    public static final int MAXIMUM_DAYS_PER_REPORT = 8;
}
