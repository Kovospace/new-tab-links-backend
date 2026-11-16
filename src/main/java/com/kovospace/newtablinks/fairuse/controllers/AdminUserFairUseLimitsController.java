package com.kovospace.newtablinks.fairuse.controllers;

import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.fairuse.dtos.UserFairUseLimitsDto;
import com.kovospace.newtablinks.fairuse.dtos.UserFairUseLimitsSaveRequestDto;
import com.kovospace.newtablinks.fairuse.services.UserFairUseOverrideAdministrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One account's raised Fair Use limits, for the operator.
 *
 * <p>Under {@link ApiEndpointPaths#ADMINISTRATION_BASE_PATH}, so refused without the
 * {@code SCOPE_ADMIN} authority. A missing account is a plain 404, as everywhere in the
 * operator's account repair.</p>
 *
 * @since 0.0.20
 */
@RestController
@RequestMapping(ApiEndpointPaths.ADMINISTRATION_BASE_PATH
        + ApiEndpointPaths.ADMINISTRATION_USER_FAIR_USE_LIMITS_SUBPATH)
@Tag(name = "Administration", description = "Operator sign-in and account repair")
public class AdminUserFairUseLimitsController {

    private final UserFairUseOverrideAdministrationService userFairUseOverrideAdministrationService;

    /**
     * Creates the controller.
     *
     * @param userFairUseOverrideAdministrationService reads, raises and removes an account's limits
     */
    public AdminUserFairUseLimitsController(
            final UserFairUseOverrideAdministrationService userFairUseOverrideAdministrationService) {

        this.userFairUseOverrideAdministrationService = userFairUseOverrideAdministrationService;
    }

    /**
     * Reads one account's Fair Use overrides and what it gets now.
     *
     * @param userId identifier of the account
     * @return its overrides, note, the global limits, its effective limits and its standing
     */
    @GetMapping
    @Operation(summary = "Read one account's raised Fair Use limits",
            description = "An account without an override has every override null and a null "
                    + "note. `effective` is what the account gets right now: its standing, the "
                    + "free plan's numbers when it is not premium, and the overrides.")
    @ApiResponse(responseCode = "200", description = "The account's Fair Use limits")
    @ApiResponse(responseCode = "404", description = "No account has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public UserFairUseLimitsDto findFairUseLimitsOf(@PathVariable final UUID userId) {
        return userFairUseOverrideAdministrationService.findFairUseLimitsOf(userId);
    }

    /**
     * Raises one account's Fair Use limits, replacing any earlier raise.
     *
     * @param userId      identifier of the account
     * @param saveRequest the raised limits and the reason
     * @return the account's Fair Use limits after the change
     */
    @PutMapping
    @Operation(summary = "Raise one account's Fair Use limits",
            description = "Replaces the account's override. Every non-null limit must be at "
                    + "least the global Fair Use value of that limit; a null one keeps the "
                    + "global value. All null removes the override, like DELETE. If the global "
                    + "value later rises past an override, the higher of the two holds. The "
                    + "account's devices are told to synchronise, so they pick up the new "
                    + "limits at once.")
    @ApiResponse(responseCode = "200", description = "The account's Fair Use limits after the change")
    @ApiResponse(responseCode = "400", description = "A limit is below 1 or below the global "
            + "Fair Use value, or the note is longer than 500 characters",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "No account has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public UserFairUseLimitsDto replaceFairUseOverrideOf(
            @PathVariable final UUID userId,
            @Valid @RequestBody final UserFairUseLimitsSaveRequestDto saveRequest) {

        return userFairUseOverrideAdministrationService.replaceFairUseOverrideOf(userId, saveRequest);
    }

    /**
     * Removes one account's raised Fair Use limits.
     *
     * @param userId identifier of the account
     * @return an empty 204
     */
    @DeleteMapping
    @Operation(summary = "Remove one account's raised Fair Use limits",
            description = "The global Fair Use limits hold for the account again. Idempotent.")
    @ApiResponse(responseCode = "204", description = "The account has no override")
    @ApiResponse(responseCode = "404", description = "No account has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> deleteFairUseOverrideOf(@PathVariable final UUID userId) {
        userFairUseOverrideAdministrationService.deleteFairUseOverrideOf(userId);
        return ResponseEntity.noContent().build();
    }
}
