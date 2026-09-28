package com.kovospace.newtablinks.statistics.controllers;

import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.statistics.dtos.NewTabReportRequestDto;
import com.kovospace.newtablinks.statistics.dtos.UsageReportAcknowledgementDto;
import com.kovospace.newtablinks.statistics.models.UsageMetric;
import com.kovospace.newtablinks.statistics.services.NewTabReportService;
import com.kovospace.newtablinks.statistics.services.UsageStatisticsRateLimiter;
import com.kovospace.newtablinks.statistics.services.WebsiteVisitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives the anonymous usage counts: new tabs from the extension, visits from the website.
 *
 * <p>Both endpoints are public and never tied to an account - the security configuration does
 * not even read a bearer token here. The caller's address is used for two things only, the rate
 * limit and the visitor hash, and is taken from {@link HttpServletRequest#getRemoteAddr()},
 * which the forwarded-header support has already set to the client behind the ingress.</p>
 *
 * @since 0.0.11
 */
@RestController
@Tag(name = "Usage statistics", description = "Anonymous daily usage counts")
public class UsageStatisticsController {

    private final NewTabReportService newTabReportService;
    private final WebsiteVisitService websiteVisitService;
    private final UsageStatisticsRateLimiter usageStatisticsRateLimiter;

    /**
     * Creates the controller.
     *
     * @param newTabReportService        adds reported new-tab counts
     * @param websiteVisitService        counts website visitors
     * @param usageStatisticsRateLimiter limits calls per address
     */
    public UsageStatisticsController(
            final NewTabReportService newTabReportService,
            final WebsiteVisitService websiteVisitService,
            final UsageStatisticsRateLimiter usageStatisticsRateLimiter) {

        this.newTabReportService = newTabReportService;
        this.websiteVisitService = websiteVisitService;
        this.usageStatisticsRateLimiter = usageStatisticsRateLimiter;
    }

    /**
     * Adds the extension's unsent new-tab counts to the daily totals.
     *
     * @param report  the days and counts not reported yet
     * @param request the HTTP request, read only for the caller's address
     * @return when to report next
     */
    @PostMapping(ApiEndpointPaths.NEW_TAB_STATISTICS_PATH)
    @SecurityRequirements
    @Operation(summary = "Report new tabs opened, per day",
            description = "Anonymous; an Authorization header is ignored. Entries whose day is "
                    + "not within seven days before to one day after the server's date (UTC), or "
                    + "whose count is not 1..2000, are dropped silently and the report still "
                    + "answers 200 - the client should discard everything it sent.")
    @ApiResponse(responseCode = "200", description = "Taken; carries when to report next")
    @ApiResponse(responseCode = "400", description = "The body is malformed, or has more than "
            + "eight entries",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "429", description = "Too many reports from this address; keep "
            + "the counts and retry after Retry-After",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public UsageReportAcknowledgementDto reportNewTabs(
            @Valid @RequestBody final NewTabReportRequestDto report,
            final HttpServletRequest request) {

        usageStatisticsRateLimiter.acquirePermit(UsageMetric.NEW_TABS, request.getRemoteAddr());
        return newTabReportService.recordReport(report);
    }

    /**
     * Counts a website visit, once per visitor per day.
     *
     * @param userAgent the visitor's browser; a missing or automated one is not counted
     * @param request   the HTTP request, read only for the caller's address
     */
    @PostMapping(ApiEndpointPaths.WEBSITE_VISIT_STATISTICS_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    @Operation(summary = "Report a website visit",
            description = "Anonymous and bodiless. Counted once per visitor per UTC day; crawlers, "
                    + "previews and scripts are recognised by User-Agent and not counted. The "
                    + "answer is the same whether or not the visit was counted.")
    @ApiResponse(responseCode = "204", description = "Received")
    @ApiResponse(responseCode = "429", description = "Too many visits reported from this address",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public void reportWebsiteVisit(
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) final String userAgent,
            final HttpServletRequest request) {

        usageStatisticsRateLimiter.acquirePermit(
                UsageMetric.WEBSITE_VISITORS, request.getRemoteAddr());
        websiteVisitService.recordVisit(request.getRemoteAddr(), userAgent);
    }
}
