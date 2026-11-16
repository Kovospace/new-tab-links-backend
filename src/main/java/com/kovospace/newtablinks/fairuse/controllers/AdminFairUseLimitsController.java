package com.kovospace.newtablinks.fairuse.controllers;

import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.fairuse.dtos.FairUseLimitsSaveRequestDto;
import com.kovospace.newtablinks.fairuse.services.FairUseLimitsAdministrationService;
import com.kovospace.newtablinks.user.dtos.PlanLimitValuesDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The global Fair Use limits, for the operator.
 *
 * <p>Under {@link ApiEndpointPaths#ADMINISTRATION_BASE_PATH}, so refused without the
 * {@code SCOPE_ADMIN} authority.</p>
 *
 * @since 0.0.20
 */
@RestController
@RequestMapping(ApiEndpointPaths.ADMINISTRATION_BASE_PATH
        + ApiEndpointPaths.ADMINISTRATION_FAIR_USE_LIMITS_SUBPATH)
@Tag(name = "Administration", description = "Operator sign-in and account repair")
public class AdminFairUseLimitsController {

    private final FairUseLimitsAdministrationService fairUseLimitsAdministrationService;

    /**
     * Creates the controller.
     *
     * @param fairUseLimitsAdministrationService reads and replaces the global limits
     */
    public AdminFairUseLimitsController(
            final FairUseLimitsAdministrationService fairUseLimitsAdministrationService) {

        this.fairUseLimitsAdministrationService = fairUseLimitsAdministrationService;
    }

    /**
     * Reads the global Fair Use limits.
     *
     * @return the limits as stored
     */
    @GetMapping
    @Operation(summary = "Read the global Fair Use limits")
    @ApiResponse(responseCode = "200", description = "The global Fair Use limits")
    public PlanLimitValuesDto findGlobalFairUseLimits() {
        return fairUseLimitsAdministrationService.findGlobalFairUseLimits();
    }

    /**
     * Replaces the global Fair Use limits.
     *
     * @param saveRequest every limit, each at least one
     * @return the limits as stored
     */
    @PutMapping
    @Operation(summary = "Replace the global Fair Use limits",
            description = "Every field is required and at least 1. Lowering a limit is allowed: "
                    + "nothing already stored is deleted, only growth past it is refused. Every "
                    + "replica picks the change up within its cache lifetime (30 seconds by "
                    + "default); no device is notified. Limits raised for single accounts stay "
                    + "as they are, and the higher of the two holds.")
    @ApiResponse(responseCode = "200", description = "The limits as stored")
    @ApiResponse(responseCode = "400", description = "A limit is missing or below 1",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public PlanLimitValuesDto replaceGlobalFairUseLimits(
            @Valid @RequestBody final FairUseLimitsSaveRequestDto saveRequest) {

        return fairUseLimitsAdministrationService.replaceGlobalFairUseLimits(saveRequest);
    }
}
