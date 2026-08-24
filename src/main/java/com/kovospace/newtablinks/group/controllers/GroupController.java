package com.kovospace.newtablinks.group.controllers;

import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.group.dtos.GroupDto;
import com.kovospace.newtablinks.group.dtos.GroupSaveRequestDto;
import com.kovospace.newtablinks.group.services.GroupService;
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
 * HTTP endpoints for groups.
 *
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/groups")
@Tag(name = "Groups", description = "Titled boxes of links inside an environment")
public class GroupController {

    private final GroupService groupService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    /**
     * Creates the controller.
     *
     * @param groupService              service holding the business logic
     * @param authenticatedUserProvider identifies the user the request is authenticated as
     */
    public GroupController(
            final GroupService groupService,
            final AuthenticatedUserProvider authenticatedUserProvider) {

        this.groupService = groupService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    /**
     * Lists an environment's groups in display order.
     *
     * @param environmentId identifier of the owning environment
     * @return the environment's groups
     */
    @GetMapping
    @Operation(summary = "List an environment's groups in display order")
    @ApiResponse(responseCode = "200", description = "The groups, possibly empty")
    public List<GroupDto> listGroupsOfEnvironment(@RequestParam final UUID environmentId) {
        return groupService.findGroupsByEnvironment(
                environmentId, authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Returns a single group.
     *
     * @param groupId identifier of the group
     * @return the group
     */
    @GetMapping("/{groupId}")
    @Operation(summary = "Return a single group")
    @ApiResponse(responseCode = "200", description = "The group")
    @ApiResponse(responseCode = "404", description = "No group has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public GroupDto getGroup(@PathVariable final UUID groupId) {
        return groupService.findGroupById(
                groupId, authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Creates a group, appended after the environment's existing ones.
     *
     * @param saveRequest the group to create
     * @return the created group
     */
    @PostMapping
    @Operation(summary = "Create a group, appended after the environment's existing ones")
    @ApiResponse(responseCode = "201", description = "The created group")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "The owning environment does not exist",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<GroupDto> createGroup(@Valid @RequestBody final GroupSaveRequestDto saveRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createGroup(
                saveRequest, authenticatedUserProvider.getAuthenticatedUserId()));
    }

    /**
     * Renames a group.
     *
     * @param groupId     identifier of the group to update
     * @param saveRequest the values to store
     * @return the updated group
     */
    @PutMapping("/{groupId}")
    @Operation(summary = "Rename a group")
    @ApiResponse(responseCode = "200", description = "The updated group")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "No group has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public GroupDto updateGroup(
            @PathVariable final UUID groupId,
            @Valid @RequestBody final GroupSaveRequestDto saveRequest) {

        return groupService.updateGroup(
                groupId, saveRequest, authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Deletes a group.
     *
     * @param groupId identifier of the group to delete
     * @return an empty response
     */
    @DeleteMapping("/{groupId}")
    @Operation(summary = "Delete a group")
    @ApiResponse(responseCode = "204", description = "The group was deleted")
    @ApiResponse(responseCode = "404", description = "No group has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> deleteGroup(@PathVariable final UUID groupId) {
        groupService.deleteGroup(groupId, authenticatedUserProvider.getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }
}
