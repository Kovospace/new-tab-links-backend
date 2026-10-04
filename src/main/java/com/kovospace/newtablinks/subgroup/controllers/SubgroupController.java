package com.kovospace.newtablinks.subgroup.controllers;

import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.subgroup.dtos.SubgroupDto;
import com.kovospace.newtablinks.subgroup.dtos.SubgroupSaveRequestDto;
import com.kovospace.newtablinks.subgroup.services.SubgroupService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for subgroups.
 *
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/subgroups")
@Tag(name = "Subgroups", description = "Collapsible sections inside a group")
public class SubgroupController {

    private final SubgroupService subgroupService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    /**
     * Creates the controller.
     *
     * @param subgroupService           service holding the business logic
     * @param authenticatedUserProvider identifies the user the request is authenticated as
     */
    public SubgroupController(
            final SubgroupService subgroupService,
            final AuthenticatedUserProvider authenticatedUserProvider) {

        this.subgroupService = subgroupService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    /**
     * Lists a group's subgroups in display order.
     *
     * @param parentGroupId identifier of the owning group
     * @return the group's subgroups
     */
    @GetMapping
    @Operation(summary = "List a group's subgroups in display order")
    @ApiResponse(responseCode = "200", description = "The subgroups, possibly empty")
    public List<SubgroupDto> listSubgroupsOfGroup(@RequestParam final UUID parentGroupId) {
        return subgroupService.findSubgroupsByParentGroup(
                parentGroupId, authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Returns a single subgroup.
     *
     * @param subgroupId identifier of the subgroup
     * @return the subgroup
     */
    @GetMapping("/{subgroupId}")
    @Operation(summary = "Return a single subgroup")
    @ApiResponse(responseCode = "200", description = "The subgroup")
    @ApiResponse(responseCode = "404", description = "No subgroup has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public SubgroupDto getSubgroup(@PathVariable final UUID subgroupId) {
        return subgroupService.findSubgroupById(
                subgroupId, authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Creates a subgroup, appended after the group's existing ones.
     *
     * @param saveRequest the subgroup to create
     * @return the created subgroup
     */
    @PostMapping
    @Operation(summary = "Create a subgroup, appended after the group's existing ones")
    @ApiResponse(responseCode = "201", description = "The created subgroup")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "The owning group does not exist",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "The group's workspace holds no synchronisation slot (limit PROFILES or WORKSPACES_PER_PROFILE), or the group already holds as many subgroups as the Fair Use Policy allows (limit SUBGROUPS_PER_GROUP). "
            + "Code FREE_PLAN_LIMIT_REACHED (a free account, and upgrading would allow it) or "
            + "FAIR_USE_LIMIT_REACHED, with limit, maximum and manageUrl (the website's "
            + "devices page)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<SubgroupDto> createSubgroup(
            @Valid @RequestBody final SubgroupSaveRequestDto saveRequest) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subgroupService.createSubgroup(
                        saveRequest, authenticatedUserProvider.getAuthenticatedUserId()));
    }

    /**
     * Updates the name, folded state, tab group setting and colour of a subgroup.
     *
     * @param subgroupId  identifier of the subgroup to update
     * @param saveRequest the values to store
     * @return the updated subgroup
     */
    @PutMapping("/{subgroupId}")
    @Operation(summary = "Update a subgroup's name, folded state, tab group setting and colour")
    @ApiResponse(responseCode = "200", description = "The updated subgroup")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "No subgroup has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "The record lies in a profile or workspace that holds no synchronisation slot (limit PROFILES or WORKSPACES_PER_PROFILE); the plan keeps it, but it no longer synchronises. "
            + "Code FREE_PLAN_LIMIT_REACHED (a free account, and upgrading would allow it) or "
            + "FAIR_USE_LIMIT_REACHED, with limit, maximum and manageUrl (the website's "
            + "devices page)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public SubgroupDto updateSubgroup(
            @PathVariable final UUID subgroupId,
            @Valid @RequestBody final SubgroupSaveRequestDto saveRequest) {

        return subgroupService.updateSubgroup(
                subgroupId, saveRequest, authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Deletes a subgroup.
     *
     * @param subgroupId identifier of the subgroup to delete
     * @return an empty response
     */
    @DeleteMapping("/{subgroupId}")
    @Operation(summary = "Delete a subgroup")
    @ApiResponse(responseCode = "204", description = "The subgroup was deleted")
    @ApiResponse(responseCode = "404", description = "No subgroup has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "The record lies in a profile or workspace that holds no synchronisation slot (limit PROFILES or WORKSPACES_PER_PROFILE); the plan keeps it, but it no longer synchronises. "
            + "Code FREE_PLAN_LIMIT_REACHED (a free account, and upgrading would allow it) or "
            + "FAIR_USE_LIMIT_REACHED, with limit, maximum and manageUrl (the website's "
            + "devices page)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> deleteSubgroup(@PathVariable final UUID subgroupId) {
        subgroupService.deleteSubgroup(
                subgroupId, authenticatedUserProvider.getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }
}
