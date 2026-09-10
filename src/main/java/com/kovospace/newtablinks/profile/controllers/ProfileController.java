package com.kovospace.newtablinks.profile.controllers;

import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.profile.dtos.ProfileDto;
import com.kovospace.newtablinks.profile.dtos.ProfileSaveRequestDto;
import com.kovospace.newtablinks.profile.services.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for profiles.
 *
 * @since 0.0.6
 */
@RestController
@RequestMapping("/api/v1/profiles")
@Tag(name = "Profiles", description = "Named sets of environments, the top of the link hierarchy")
public class ProfileController {

    private final ProfileService profileService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    /**
     * Creates the controller.
     *
     * @param profileService            service holding the business logic
     * @param authenticatedUserProvider identifies the user the request is authenticated as
     */
    public ProfileController(
            final ProfileService profileService,
            final AuthenticatedUserProvider authenticatedUserProvider) {

        this.profileService = profileService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    /**
     * Lists the signed-in user's profiles in display order.
     *
     * @return the caller's profiles
     */
    @GetMapping
    @Operation(summary = "List the signed-in user's profiles in display order")
    @ApiResponse(responseCode = "200", description = "The profiles, possibly empty")
    public List<ProfileDto> listMyProfiles() {
        return profileService.findProfilesByOwner(
                authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Returns a single profile.
     *
     * @param profileId identifier of the profile
     * @return the profile
     */
    @GetMapping("/{profileId}")
    @Operation(summary = "Return a single profile")
    @ApiResponse(responseCode = "200", description = "The profile")
    @ApiResponse(responseCode = "404", description = "No profile has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ProfileDto getProfile(@PathVariable final UUID profileId) {
        return profileService.findProfileById(
                profileId, authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Creates a profile, appended after the owner's existing ones.
     *
     * @param saveRequest the profile to create
     * @return the created profile
     */
    @PostMapping
    @Operation(summary = "Create a profile, appended after the owner's existing ones")
    @ApiResponse(responseCode = "201", description = "The created profile")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "The owning user does not exist",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<ProfileDto> createProfile(
            @Valid @RequestBody final ProfileSaveRequestDto saveRequest) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(profileService.createProfile(
                        saveRequest, authenticatedUserProvider.getAuthenticatedUserId()));
    }

    /**
     * Updates the name and settings of a profile.
     *
     * @param profileId   identifier of the profile to update
     * @param saveRequest the values to store
     * @return the updated profile
     */
    @PutMapping("/{profileId}")
    @Operation(summary = "Update the name and settings of a profile")
    @ApiResponse(responseCode = "200", description = "The updated profile")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "No profile has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ProfileDto updateProfile(
            @PathVariable final UUID profileId,
            @Valid @RequestBody final ProfileSaveRequestDto saveRequest) {

        return profileService.updateProfile(
                profileId, saveRequest, authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Deletes a profile and everything inside it.
     *
     * @param profileId identifier of the profile to delete
     * @return an empty response
     */
    @DeleteMapping("/{profileId}")
    @Operation(summary = "Delete a profile and every environment inside it")
    @ApiResponse(responseCode = "204", description = "The profile was deleted")
    @ApiResponse(responseCode = "404", description = "No profile has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> deleteProfile(@PathVariable final UUID profileId) {
        profileService.deleteProfile(
                profileId, authenticatedUserProvider.getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }
}
