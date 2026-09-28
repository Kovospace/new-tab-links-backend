package com.kovospace.newtablinks.statistics.controllers;

import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.statistics.dtos.UsageMetricMonthDto;
import com.kovospace.newtablinks.statistics.models.UsageMetric;
import com.kovospace.newtablinks.statistics.services.UsageMetricReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shows the operator the usage counts, one metric and one month at a time.
 *
 * <p>Under {@link ApiEndpointPaths#ADMINISTRATION_BASE_PATH}, so the security configuration
 * demands an admin token for it; nothing here checks that again.</p>
 *
 * @since 0.0.11
 */
@RestController
@RequestMapping(ApiEndpointPaths.ADMINISTRATION_BASE_PATH)
@Tag(name = "Administration", description = "Operator-only endpoints")
public class AdminUsageMetricsController {

    private final UsageMetricReportService usageMetricReportService;

    /**
     * Creates the controller.
     *
     * @param usageMetricReportService reads the monthly figures
     */
    public AdminUsageMetricsController(final UsageMetricReportService usageMetricReportService) {
        this.usageMetricReportService = usageMetricReportService;
    }

    /**
     * Returns one metric's daily totals over one month.
     *
     * @param metric the metric, by identifier
     * @param month  the month, as {@code YYYY-MM}
     * @return every day of the month, zero where nothing was counted, and the month's total
     */
    @GetMapping(ApiEndpointPaths.ADMINISTRATION_METRICS_SUBPATH)
    @Operation(summary = "Read a usage metric for one month",
            description = "Every day of the month is present, 0 where nothing was counted. "
                    + "new_tabs days are the extension's local dates; website_visitors days are "
                    + "UTC. The two metrics are separate and never summed.")
    @ApiResponse(responseCode = "200", description = "The month's figures")
    @ApiResponse(responseCode = "400", description = "Unknown metric, or month not YYYY-MM",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "403", description = "The token is not an admin token",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public UsageMetricMonthDto getMonthlyUsageMetric(
            @Parameter(description = "The metric", example = "new_tabs",
                    schema = @Schema(type = "string",
                            allowableValues = {"new_tabs", "website_visitors"}))
            @RequestParam final UsageMetric metric,
            @Parameter(description = "The month", example = "2026-09",
                    schema = @Schema(type = "string", pattern = "^\\d{4}-\\d{2}$"))
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") final YearMonth month) {

        return usageMetricReportService.describeMonth(metric, month);
    }
}
