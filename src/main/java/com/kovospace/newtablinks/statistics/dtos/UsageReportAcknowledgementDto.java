package com.kovospace.newtablinks.statistics.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The answer to a new-tab report: taken, and when to send the next one.
 *
 * @param nextReportAfterSeconds how long the extension waits before reporting again
 * @since 0.0.11
 */
@Schema(description = "Acknowledges a new-tab report")
public record UsageReportAcknowledgementDto(

        @Schema(description = "Seconds to wait before the next report. Server configuration, so "
                + "the reporting interval changes without an extension release.", example = "900")
        int nextReportAfterSeconds) {
}
