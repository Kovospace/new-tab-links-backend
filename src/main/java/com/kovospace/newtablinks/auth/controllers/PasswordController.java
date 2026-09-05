package com.kovospace.newtablinks.auth.controllers;

import com.kovospace.newtablinks.auth.dtos.PasswordChangeRequestDto;
import com.kovospace.newtablinks.auth.dtos.PasswordResetConfirmationDto;
import com.kovospace.newtablinks.auth.dtos.PasswordResetRequestDto;
import com.kovospace.newtablinks.auth.dtos.RegistrationAcceptedDto;
import com.kovospace.newtablinks.auth.services.PasswordService;
import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for setting, changing and resetting a password.
 *
 * @since 0.0.3
 */
@RestController
@RequestMapping("/api/v1/auth/password")
@Tag(name = "Password", description = "Setting, changing and resetting a password")
public class PasswordController {

    /**
     * Wording returned to every reset request, whether or not anything was sent.
     */
    private static final String UNIFORM_RESET_MESSAGE =
            "If that address belongs to an account, a reset link is on its way.";

    private final PasswordService passwordService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    /**
     * Creates the controller.
     *
     * @param passwordService           service holding the business logic
     * @param authenticatedUserProvider identifies the user the request is authenticated as
     */
    public PasswordController(
            final PasswordService passwordService,
            final AuthenticatedUserProvider authenticatedUserProvider) {

        this.passwordService = passwordService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    /**
     * Asks for a password reset link.
     *
     * @param resetRequest the address to send to
     * @return the uniform acknowledgement
     */
    @PostMapping("/reset-request")
    @Operation(summary = "Ask for a password reset link",
            description = "Answers identically whether or not the address belongs to an account, "
                    + "so that this endpoint cannot be used to discover who is registered.")
    @ApiResponse(responseCode = "202", description = "The request was accepted")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<RegistrationAcceptedDto> requestPasswordReset(
            @Valid @RequestBody final PasswordResetRequestDto resetRequest) {

        passwordService.requestPasswordReset(resetRequest.email());
        // Delivery is deliberately not reported here: an address with no account sends nothing
        // at all, so a delivery outcome would say which of those two happened.
        return ResponseEntity.accepted()
                .body(RegistrationAcceptedDto.withoutDeliveryReport(UNIFORM_RESET_MESSAGE));
    }

    /**
     * Sets a new password from a reset link.
     *
     * @param confirmation the token and the new password
     * @return an empty response
     */
    @PostMapping("/reset-confirm")
    @Operation(summary = "Set a new password from a reset link",
            description = "Signs every device out, because a password is usually reset precisely "
                    + "when the old one cannot be trusted.")
    @ApiResponse(responseCode = "204", description = "The password was set")
    @ApiResponse(responseCode = "400", description = "The token is unknown, spent, or expired",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody final PasswordResetConfirmationDto confirmation) {

        passwordService.confirmPasswordReset(confirmation);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sets or changes the signed-in user's password.
     *
     * @param changeRequest the current password, if any, and the new one
     * @return an empty response
     */
    @PostMapping("/change")
    @Operation(summary = "Set or change the signed-in user's password",
            description = "`currentPassword` is required only when the account already has one. "
                    + "An account created through Google has none, and this is how its owner "
                    + "gives it one - there is nothing to prove, because the caller is already "
                    + "authenticated. Signs every device out either way.")
    @ApiResponse(responseCode = "204", description = "The password was set")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "401", description = "No valid access token, or the current "
            + "password was wrong",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> setOrChangePassword(
            @Valid @RequestBody final PasswordChangeRequestDto changeRequest) {

        passwordService.setOrChangePassword(
                authenticatedUserProvider.getAuthenticatedUserId(), changeRequest);
        return ResponseEntity.noContent().build();
    }
}
