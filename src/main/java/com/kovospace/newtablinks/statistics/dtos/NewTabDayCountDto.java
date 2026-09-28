package com.kovospace.newtablinks.statistics.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * How many new tabs one installation opened on one day.
 *
 * <p>Only presence is validated here. A day or count outside what the server accepts is not a
 * malformed body - the report still succeeds and the entry is dropped - because the extension
 * would otherwise keep resending an entry that can never succeed.</p>
 *
 * @param day   the day, in the client's own time zone
 * @param count new tabs opened on it
 * @since 0.0.11
 */
@Schema(description = "New tabs opened on one day")
public record NewTabDayCountDto(

        @Schema(description = "The day, as the client's local date. Accepted from seven days "
                + "before the server's date (UTC) to one day after it; others are dropped.",
                example = "2026-09-28")
        @NotNull
        LocalDate day,

        @Schema(description = "New tabs opened that day. Accepted from 1 to 2000; others are "
                + "dropped.", example = "12")
        @NotNull
        Long count) {
}
