package com.kovospace.newtablinks.admin.controllers;

import com.kovospace.newtablinks.admin.dtos.AdminSessionDto;
import com.kovospace.newtablinks.admin.dtos.AdminSignInRequestDto;
import com.kovospace.newtablinks.admin.services.AdminSignInService;
import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The operator's way in.
 *
 * @since 0.0.6
 */
@RestController
@RequestMapping(ApiEndpointPaths.ADMINISTRATION_BASE_PATH)
@Tag(name = "Administration", description = "Operator sign-in and account repair")
public class AdminAuthenticationController {

    private final AdminSignInService adminSignInService;

    /**
     * Creates the controller.
     *
     * @param adminSignInService checks the credentials and issues the token
     */
    public AdminAuthenticationController(final AdminSignInService adminSignInService) {
        this.adminSignInService = adminSignInService;
    }

    /**
     * Signs the operator in.
     *
     * @param signInRequest the credentials presented
     * @return the short-lived admin session
     */
    @PostMapping(ApiEndpointPaths.ADMINISTRATION_SIGN_IN_SUBPATH)
    @Operation(summary = "Sign in as the operator",
            description = "Exchanges the configured administrator credentials for a short-lived "
                    + "bearer token that admits the caller to the rest of /api/v1/admin. "
                    + "There is no refresh token: when it expires, sign in again. "
                    + "Refusals are worded identically whether the username was wrong, the "
                    + "password was wrong, or this deployment has no administrator configured.")
    @ApiResponse(responseCode = "200", description = "The credentials were accepted")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "401", description = "The credentials were refused",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "429",
            description = "Too many failed attempts; Retry-After says how long the lock lasts",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public AdminSessionDto signIn(@Valid @RequestBody final AdminSignInRequestDto signInRequest) {
        return adminSignInService.signIn(signInRequest);
    }
}
