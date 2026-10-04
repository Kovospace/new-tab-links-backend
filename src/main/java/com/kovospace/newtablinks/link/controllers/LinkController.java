package com.kovospace.newtablinks.link.controllers;

import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.link.dtos.LinkDto;
import com.kovospace.newtablinks.link.dtos.LinkSaveRequestDto;
import com.kovospace.newtablinks.link.services.LinkService;
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
 * HTTP endpoints for links.
 *
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/links")
@Tag(name = "Links", description = "The bookmarks rendered on the new tab page")
public class LinkController {

    private final LinkService linkService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    /**
     * Creates the controller.
     *
     * @param linkService               service holding the business logic
     * @param authenticatedUserProvider identifies the user the request is authenticated as
     */
    public LinkController(
            final LinkService linkService,
            final AuthenticatedUserProvider authenticatedUserProvider) {

        this.linkService = linkService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    /**
     * Lists the links sitting directly in a group, excluding those nested in its subgroups.
     *
     * @param parentGroupId identifier of the owning group
     * @return the group's direct links
     */
    @GetMapping("/direct")
    @Operation(summary = "List the links sitting directly in a group, excluding its subgroups")
    @ApiResponse(responseCode = "200", description = "The links, possibly empty")
    public List<LinkDto> listDirectLinksOfGroup(@RequestParam final UUID parentGroupId) {
        return linkService.findDirectLinksOfGroup(
                parentGroupId, authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Lists the links nested in a subgroup.
     *
     * @param parentSubgroupId identifier of the owning subgroup
     * @return the subgroup's links
     */
    @GetMapping
    @Operation(summary = "List the links nested in a subgroup")
    @ApiResponse(responseCode = "200", description = "The links, possibly empty")
    public List<LinkDto> listLinksOfSubgroup(@RequestParam final UUID parentSubgroupId) {
        return linkService.findLinksOfSubgroup(
                parentSubgroupId, authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Returns a single link.
     *
     * @param linkId identifier of the link
     * @return the link
     */
    @GetMapping("/{linkId}")
    @Operation(summary = "Return a single link")
    @ApiResponse(responseCode = "200", description = "The link")
    @ApiResponse(responseCode = "404", description = "No link has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public LinkDto getLink(@PathVariable final UUID linkId) {
        return linkService.findLinkById(
                linkId, authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Creates a link, appended after its siblings.
     *
     * @param saveRequest the link to create
     * @return the created link
     */
    @PostMapping
    @Operation(summary = "Create a link, appended after its siblings")
    @ApiResponse(responseCode = "201", description = "The created link")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "The named group or subgroup does not exist",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "The workspace holds no synchronisation slot (limit PROFILES or WORKSPACES_PER_PROFILE), or already holds as many links as the Fair Use Policy allows (limit LINKS_PER_WORKSPACE). "
            + "Code FREE_PLAN_LIMIT_REACHED (a free account, and upgrading would allow it) or "
            + "FAIR_USE_LIMIT_REACHED, with limit, maximum and manageUrl (the website's "
            + "devices page)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<LinkDto> createLink(@Valid @RequestBody final LinkSaveRequestDto saveRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(linkService.createLink(
                saveRequest, authenticatedUserProvider.getAuthenticatedUserId()));
    }

    /**
     * Replaces the changeable fields of a link, including which subgroup it sits in.
     *
     * @param linkId      identifier of the link to update
     * @param saveRequest the values to store
     * @return the updated link
     */
    @PutMapping("/{linkId}")
    @Operation(summary = "Replace the changeable fields of a link")
    @ApiResponse(responseCode = "200", description = "The updated link")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "The link, or the named subgroup, does not exist",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "The record lies in a profile or workspace that holds no synchronisation slot (limit PROFILES or WORKSPACES_PER_PROFILE); the plan keeps it, but it no longer synchronises. "
            + "Code FREE_PLAN_LIMIT_REACHED (a free account, and upgrading would allow it) or "
            + "FAIR_USE_LIMIT_REACHED, with limit, maximum and manageUrl (the website's "
            + "devices page)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public LinkDto updateLink(
            @PathVariable final UUID linkId,
            @Valid @RequestBody final LinkSaveRequestDto saveRequest) {

        return linkService.updateLink(
                linkId, saveRequest, authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Deletes a link.
     *
     * @param linkId identifier of the link to delete
     * @return an empty response
     */
    @DeleteMapping("/{linkId}")
    @Operation(summary = "Delete a link")
    @ApiResponse(responseCode = "204", description = "The link was deleted")
    @ApiResponse(responseCode = "404", description = "No link has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "The record lies in a profile or workspace that holds no synchronisation slot (limit PROFILES or WORKSPACES_PER_PROFILE); the plan keeps it, but it no longer synchronises. "
            + "Code FREE_PLAN_LIMIT_REACHED (a free account, and upgrading would allow it) or "
            + "FAIR_USE_LIMIT_REACHED, with limit, maximum and manageUrl (the website's "
            + "devices page)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> deleteLink(@PathVariable final UUID linkId) {
        linkService.deleteLink(linkId, authenticatedUserProvider.getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }
}
