package com.kovospace.newtablinks.user.controllers;

import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.user.dtos.UserDeviceDto;
import com.kovospace.newtablinks.user.services.UserDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import com.kovospace.newtablinks.auth.dtos.ClientDescriptionDto;
import com.kovospace.newtablinks.common.config.ClientRequestHeaders;
import com.kovospace.newtablinks.user.dtos.DeviceRenameRequestDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for the places the signed-in user's account has been used.
 *
 * @since 0.0.3
 */
@RestController
@RequestMapping("/api/v1/users/me/devices")
@Tag(name = "Devices", description = "Where the account has been signed in from")
public class UserDeviceController {

    private final UserDeviceService userDeviceService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    /**
     * Creates the controller.
     *
     * @param userDeviceService         service holding the business logic
     * @param authenticatedUserProvider identifies the user the request is authenticated as
     */
    public UserDeviceController(
            final UserDeviceService userDeviceService,
            final AuthenticatedUserProvider authenticatedUserProvider) {

        this.userDeviceService = userDeviceService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    /**
     * Lists the machine and browser pairs the account has been used from.
     *
     * @return the devices, most recently used first
     */
    @GetMapping
    @Operation(summary = "List where the account has been signed in from",
            description = "A history, not a list of live sessions: a device stays listed after "
                    + "its session ends. `signedIn` says which ones still hold a usable session, "
                    + "and `lastUsedAt` when each was last seen.")
    @ApiResponse(responseCode = "200", description = "The devices, most recently used first")
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public List<UserDeviceDto> listMyDevices() {
        return userDeviceService.findDevicesOfUser(
                authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Renames one device.
     *
     * @param deviceId      identifier of the device to rename
     * @param renameRequest the new label
     * @return an empty response
     */
    @PatchMapping("/{deviceId}")
    @Operation(summary = "Rename one device",
            description = "Changes the label only. A device is identified by the installation "
                    + "that reported it, so two devices may share a name without merging, and "
                    + "duplicates are neither refused nor reported here.")
    @ApiResponse(responseCode = "204", description = "The device was renamed")
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "No such device belongs to this account",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> renameDevice(
            @PathVariable final UUID deviceId,
            @Valid @RequestBody final DeviceRenameRequestDto renameRequest) {

        userDeviceService.renameDevice(
                deviceId,
                authenticatedUserProvider.getAuthenticatedUserId(),
                renameRequest.name());

        return ResponseEntity.noContent().build();
    }

    /**
     * Hands one device over to the installation making the request.
     *
     * @param deviceId       identifier of the device to take over
     * @param installationId installation making the request
     * @return an empty response
     */
    @PostMapping("/{deviceId}/take-over")
    @Operation(summary = "Take one device over for this installation",
            description = "For a reinstall: this installation adopts the device row the user "
                    + "recognises, keeping its history and the date it was first seen. The "
                    + "installation that held it is signed out, and the row this one was given "
                    + "when it signed in is removed. Repeating it is harmless.")
    @ApiResponse(responseCode = "204", description = "The device now belongs to this installation")
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "No such device belongs to this account",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "The take-over would give a free account one "
            + "more synchronised installation than its plan allows - only possible when this "
            + "installation has no device of its own and the target carries none. Code "
            + "FREE_PLAN_LIMIT_REACHED, limit DEVICES, and the plan's maximum",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> takeOverDevice(
            @PathVariable final UUID deviceId,
            @RequestHeader(value = ClientRequestHeaders.INSTALLATION_ID, required = false)
                    final String installationId) {

        userDeviceService.takeOverDevice(
                deviceId,
                authenticatedUserProvider.getAuthenticatedUserId(),
                ClientDescriptionDto.from(null, null, installationId).installationId());

        return ResponseEntity.noContent().build();
    }

    /**
     * Signs one device out, and deletes it as well when the caller asks for that.
     *
     * <p>Which of the two operations is performed is the caller's to state, so this method routes
     * to one service method or the other rather than passing the flag on. Neither operation is
     * expressible in terms of the other: one deliberately keeps the row, the other deliberately
     * destroys it.</p>
     *
     * @param deviceId        identifier of the device to sign out
     * @param deleteAndForget whether to delete the device as well as ending its session
     * @return an empty response
     */
    @DeleteMapping("/{deviceId}")
    @Operation(summary = "Sign one device out, and optionally forget it entirely",
            description = "Two behaviours on one endpoint, chosen by `deleteAndForget`.\n\n"
                    + "Absent or `false` - the behaviour this endpoint has always had: every "
                    + "token the device holds is revoked and the device stays in the list, "
                    + "because the history of where the account has been used is worth keeping.\n\n"
                    + "`true` - the device is deleted along with its tokens and stops appearing "
                    + "in the list at all. This is permanent: the row's history, the date it was "
                    + "first seen included, goes with it, and signing in from that browser again "
                    + "records a new device rather than restoring this one. It is allowed on a "
                    + "device that still holds a live session, which the same call signs out - "
                    + "including the device the caller is making the request from, whose session "
                    + "then lasts only until its access token expires.")
    @ApiResponse(responseCode = "204",
            description = "The device was signed out, and deleted when `deleteAndForget` was set")
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "No such device belongs to this account",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> signOutOrForgetDevice(
            @PathVariable final UUID deviceId,
            @Parameter(description = "Delete the device as well as signing it out, removing it "
                    + "from the list for good. Defaults to false, which signs out and keeps it.")
            @RequestParam(defaultValue = "false") final boolean deleteAndForget) {

        final UUID accountId = authenticatedUserProvider.getAuthenticatedUserId();

        if (deleteAndForget) {
            userDeviceService.signOutAndForgetDevice(deviceId, accountId);
        } else {
            userDeviceService.signOutDevice(deviceId, accountId);
        }

        return ResponseEntity.noContent().build();
    }
}
