package com.kovospace.newtablinks.user.controllers;

import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.user.dtos.PlanLimitsDto;
import com.kovospace.newtablinks.user.dtos.UserDto;
import com.kovospace.newtablinks.user.dtos.UserProfileUpdateRequestDto;
import com.kovospace.newtablinks.user.services.AccountPlanLimitsService;
import com.kovospace.newtablinks.user.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for the signed-in user's own account.
 *
 * <p>Accounts are not created here - that is registration's job, in
 * {@link com.kovospace.newtablinks.auth.controllers.AuthenticationController} - and one user can
 * never address another. Every endpoint acts on the caller.</p>
 *
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "The signed-in user's own account")
public class UserController {

    private final UserService userService;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final AccountPlanLimitsService accountPlanLimitsService;

    /**
     * Creates the controller.
     *
     * @param userService               service holding the business logic
     * @param authenticatedUserProvider identifies the user the request is authenticated as
     * @param accountPlanLimitsService  tells which limits hold for the account
     */
    public UserController(
            final UserService userService,
            final AuthenticatedUserProvider authenticatedUserProvider,
            final AccountPlanLimitsService accountPlanLimitsService) {

        this.userService = userService;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.accountPlanLimitsService = accountPlanLimitsService;
    }

    /**
     * Returns the plan limits that hold for the signed-in user's account right now.
     *
     * @return the account's standing and limits
     * @since 0.0.18
     */
    @GetMapping("/me/plan-limits")
    @Operation(summary = "Return the plan limits that hold for the account right now",
            description = "Judged from the live entitlement: the free plan's limits while the "
                    + "account is not premium, the Fair Use Policy's while it is. The extension "
                    + "applies these instead of its compiled defaults, so a limit can change "
                    + "without an extension release. The same object is `planLimits` on the sync "
                    + "snapshot.")
    @ApiResponse(responseCode = "200", description = "The account's standing and limits")
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public PlanLimitsDto getMyPlanLimits() {
        return accountPlanLimitsService.findPlanLimitsOf(
                authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Returns the signed-in user's own account.
     *
     * @return the caller's account
     */
    @GetMapping("/me")
    @Operation(summary = "Return the signed-in user's own account")
    @ApiResponse(responseCode = "200", description = "The account")
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public UserDto getMyAccount() {
        return userService.findUserById(authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Changes the signed-in user's display name.
     *
     * @param updateRequest the values to store
     * @return the updated account
     */
    @PutMapping("/me")
    @Operation(summary = "Change the signed-in user's display name",
            description = "The email address is not changed here: moving an account to a new "
                    + "address has to prove the new one first, which is its own flow.")
    @ApiResponse(responseCode = "200", description = "The updated account")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public UserDto updateMyAccount(
            @Valid @RequestBody final UserProfileUpdateRequestDto updateRequest) {

        return userService.updateProfile(
                authenticatedUserProvider.getAuthenticatedUserId(), updateRequest);
    }

    /**
     * Deletes the signed-in user's account and everything it owns.
     *
     * @return an empty response
     */
    @DeleteMapping("/me")
    @Operation(summary = "Delete the signed-in user's account and everything it owns")
    @ApiResponse(responseCode = "204", description = "The account was deleted")
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> deleteMyAccount() {
        userService.deleteUser(authenticatedUserProvider.getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }
}
