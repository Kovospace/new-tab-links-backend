package com.kovospace.newtablinks.user.controllers;

import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.user.dtos.AdminUserCreateRequestDto;
import com.kovospace.newtablinks.user.dtos.AdminUserDto;
import com.kovospace.newtablinks.user.dtos.AdminUserPageDto;
import com.kovospace.newtablinks.user.dtos.AdminUserPasswordRequestDto;
import com.kovospace.newtablinks.user.dtos.AdminUserUpdateRequestDto;
import com.kovospace.newtablinks.user.services.UserAdministrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading and repairing accounts, for the operator.
 *
 * <p>Every path here sits under {@link ApiEndpointPaths#ADMINISTRATION_BASE_PATH} and is
 * therefore refused without the {@code SCOPE_ADMIN} authority, which only
 * {@code AdminAccessTokenIssuer} grants and only the operator sign-in produces. A user's own
 * access token has no scope claim and cannot reach any of it.</p>
 *
 * <p>Two habits here differ from the rest of this API on purpose. Nothing is ownership-scoped -
 * the operator names an account by identifier and gets it. And a missing account is a plain 404,
 * where an ordinary endpoint would answer 404 to hide that the row exists at all; there is
 * nothing to hide from a caller who can already list every account.</p>
 *
 * @since 0.0.6
 */
@RestController
@RequestMapping(ApiEndpointPaths.ADMINISTRATION_BASE_PATH + "/users")
@Validated
@Tag(name = "Administration", description = "Operator sign-in and account repair")
public class AdminUserController {

    /** Accounts per page when the caller does not say. */
    private static final String DEFAULT_PAGE_SIZE = "25";

    /** The largest page anyone may ask for, so one request cannot pull the whole table. */
    private static final int MAXIMUM_PAGE_SIZE = 200;

    private final UserAdministrationService userAdministrationService;

    /**
     * Creates the controller.
     *
     * @param userAdministrationService what the operator can do to an account
     */
    public AdminUserController(final UserAdministrationService userAdministrationService) {
        this.userAdministrationService = userAdministrationService;
    }

    /**
     * Lists accounts, newest first.
     *
     * @param query text to match against username, email and display name
     * @param page  zero-based page index
     * @param size  how many accounts to return
     * @return the requested page
     */
    @GetMapping
    @Operation(summary = "List accounts, newest first",
            description = "The search matches username, email and display name, "
                    + "case-insensitively and anywhere in the value. An empty search lists "
                    + "everything.")
    @ApiResponse(responseCode = "200", description = "The requested page")
    public AdminUserPageDto listAccounts(
            @RequestParam(defaultValue = "") final String query,
            @RequestParam(defaultValue = "0") @Min(0) final int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAXIMUM_PAGE_SIZE) final int size) {

        return userAdministrationService.listAccounts(query, page, size);
    }

    /**
     * Reads one account.
     *
     * @param userId identifier of the account
     * @return the account
     */
    @GetMapping("/{userId}")
    @Operation(summary = "Read one account")
    @ApiResponse(responseCode = "200", description = "The account")
    @ApiResponse(responseCode = "404", description = "No account has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public AdminUserDto findAccount(@PathVariable final UUID userId) {
        return userAdministrationService.findAccount(userId);
    }

    /**
     * Creates an account without going through registration.
     *
     * @param createRequest the account to create
     * @return the created account
     */
    @PostMapping
    @Operation(summary = "Create an account without going through registration",
            description = "No activation mail is sent and no uniform answer is given, so an "
                    + "account created ACTIVE can be signed into immediately. The password is "
                    + "optional; without one the account can only sign in through a provider.")
    @ApiResponse(responseCode = "201", description = "The account was created")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "The username or email is already taken",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<AdminUserDto> createAccount(
            @Valid @RequestBody final AdminUserCreateRequestDto createRequest) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userAdministrationService.createAccount(createRequest));
    }

    /**
     * Replaces the fields an operator may change.
     *
     * @param userId        identifier of the account
     * @param updateRequest the new values
     * @return the updated account
     */
    @PutMapping("/{userId}")
    @Operation(summary = "Change an account's email, display name and status",
            description = "All three are required: this replaces them rather than patching, so a "
                    + "partial body cannot quietly blank a field. The email address is "
                    + "changeable here and nowhere else - users may not change their own.")
    @ApiResponse(responseCode = "200", description = "The updated account")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "No account has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "That email belongs to another account",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public AdminUserDto updateAccount(
            @PathVariable final UUID userId,
            @Valid @RequestBody final AdminUserUpdateRequestDto updateRequest) {

        return userAdministrationService.updateAccount(userId, updateRequest);
    }

    /**
     * Clears the failed sign-in counter that locks an account out.
     *
     * @param userId identifier of the account
     * @return the unlocked account
     */
    @PostMapping("/{userId}/unlock")
    @Operation(summary = "Clear an account's failed sign-in counter",
            description = "For the account locked out by repeated wrong passwords. It changes "
                    + "nothing else, and does not touch the password.")
    @ApiResponse(responseCode = "200", description = "The unlocked account")
    @ApiResponse(responseCode = "404", description = "No account has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public AdminUserDto unlockAccount(@PathVariable final UUID userId) {
        return userAdministrationService.unlockAccount(userId);
    }

    /**
     * Sets or removes an account's password.
     *
     * @param userId          identifier of the account
     * @param passwordRequest the password to set
     * @return the account
     */
    @PutMapping("/{userId}/password")
    @Operation(summary = "Set or remove an account's password",
            description = "For an owner who cannot get back in at all - try the reset mail "
                    + "first. The account's owner is not told. An absent password removes it, "
                    + "leaving an account that can only sign in through a provider.")
    @ApiResponse(responseCode = "200", description = "The account")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "No account has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public AdminUserDto setPassword(
            @PathVariable final UUID userId,
            @Valid @RequestBody final AdminUserPasswordRequestDto passwordRequest) {

        return userAdministrationService.setPassword(userId, passwordRequest.password());
    }

    /**
     * Deletes an account and everything it owns.
     *
     * @param userId identifier of the account
     * @return an empty response
     */
    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete an account and everything it owns",
            description = "Irreversible, and wider than it looks: every environment, group, "
                    + "subgroup and link the account owns is deleted with it, along with its "
                    + "devices and sessions. Setting the status to DISABLED is the reversible "
                    + "option, and usually the one that was meant.")
    @ApiResponse(responseCode = "204", description = "The account was deleted")
    @ApiResponse(responseCode = "404", description = "No account has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> deleteAccount(@PathVariable final UUID userId) {
        userAdministrationService.deleteAccount(userId);
        return ResponseEntity.noContent().build();
    }
}
