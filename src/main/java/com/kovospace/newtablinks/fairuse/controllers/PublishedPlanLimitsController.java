package com.kovospace.newtablinks.fairuse.controllers;

import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.fairuse.dtos.PublishedPlanLimitsDto;
import com.kovospace.newtablinks.fairuse.services.PublishedPlanLimitsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The plans' limits as published to anybody - what the website's price table and Fair Use page
 * show before anybody signs in.
 *
 * <p>Public and token-blind, like the price list: the security configuration opens exactly this
 * path and method and ignores any bearer token sent to it.</p>
 *
 * @since 0.0.20
 */
@RestController
@Tag(name = "Plan limits", description = "The free plan's and the Fair Use Policy's limits")
public class PublishedPlanLimitsController {

    /** How long a browser or proxy may reuse an answer; the server caches the row as long. */
    private static final Duration PUBLIC_CACHE_LIFETIME = Duration.ofSeconds(60);

    private final PublishedPlanLimitsService publishedPlanLimitsService;

    /**
     * Creates the controller.
     *
     * @param publishedPlanLimitsService both plans' limits
     */
    public PublishedPlanLimitsController(final PublishedPlanLimitsService publishedPlanLimitsService) {
        this.publishedPlanLimitsService = publishedPlanLimitsService;
    }

    /**
     * Returns the free plan's and the global Fair Use limits.
     *
     * @return both sets, cacheable by anybody for a minute
     */
    @GetMapping(ApiEndpointPaths.PUBLISHED_PLAN_LIMITS_PATH)
    @Operation(summary = "Read the free plan's and the Fair Use Policy's limits",
            description = "Public: no access token is needed, and one that is sent is ignored. "
                    + "`fairUse` is the global Fair Use Policy - what a premium account gets; "
                    + "`free` is what a free account gets: the free plan's own numbers over the "
                    + "Fair Use ones. Limits raised for single accounts are not reflected here. "
                    + "Cacheable for 60 seconds.")
    @ApiResponse(responseCode = "200", description = "Both plans' limits")
    public ResponseEntity<PublishedPlanLimitsDto> findPublishedPlanLimits() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(PUBLIC_CACHE_LIFETIME).cachePublic())
                .body(publishedPlanLimitsService.findPublishedPlanLimits());
    }
}
