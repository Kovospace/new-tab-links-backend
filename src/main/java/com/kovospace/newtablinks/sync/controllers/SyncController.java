package com.kovospace.newtablinks.sync.controllers;

import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.sync.dtos.SyncPushRequestDto;
import com.kovospace.newtablinks.sync.dtos.SyncPushResultDto;
import com.kovospace.newtablinks.sync.dtos.SyncSnapshotDto;
import com.kovospace.newtablinks.sync.services.SyncPushService;
import com.kovospace.newtablinks.sync.services.SyncSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for synchronizing a whole account with a client.
 *
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/sync")
@Tag(name = "Synchronization", description = "Whole-account transfer between server and extension")
public class SyncController {

    private final SyncSnapshotService syncSnapshotService;
    private final SyncPushService syncPushService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    /**
     * Creates the controller.
     *
     * @param syncSnapshotService       service assembling the snapshot
     * @param syncPushService           service applying a pushed batch of changes
     * @param authenticatedUserProvider identifies the user the request is authenticated as
     */
    public SyncController(
            final SyncSnapshotService syncSnapshotService,
            final SyncPushService syncPushService,
            final AuthenticatedUserProvider authenticatedUserProvider) {

        this.syncSnapshotService = syncSnapshotService;
        this.syncPushService = syncPushService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    /**
     * Returns everything the signed-in user owns, as flat collections.
     *
     * <p>There is no path variable naming whose snapshot to return: a snapshot is always the
     * caller's own.</p>
     *
     * @return the snapshot
     */
    @GetMapping("/snapshot")
    @Operation(summary = "Return everything the signed-in user owns, as flat collections")
    @ApiResponse(responseCode = "200", description = "The snapshot")
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public SyncSnapshotDto getMySnapshot() {
        return syncSnapshotService.captureSnapshotForUser(
                authenticatedUserProvider.getAuthenticatedUserId());
    }

    /**
     * Applies a batch of changes one device made, in the order it made them.
     *
     * <p>There is no path variable naming whose data to change, for the same reason the snapshot
     * has none: a push always changes the caller's own.</p>
     *
     * @param pushRequest the batch to apply
     * @return the identifiers the server could not keep, and the operations it refused
     */
    @PostMapping("/push")
    @Operation(summary = "Apply a batch of changes made on one device",
            description = "The whole batch is applied in one transaction, strictly in the order "
                    + "given, so the caller must order parents before their children and delete "
                    + "children before their parents. Applying the same batch twice is harmless: "
                    + "an upsert of something that already exists updates it, and a delete of "
                    + "something already gone does nothing. The account's plan is judged on the "
                    + "state after the whole batch: it is refused entirely with 409 when it wrote "
                    + "into a profile or workspace that holds no synchronisation slot at the end "
                    + "(the account's first profiles, and the first workspaces of each, in the "
                    + "order the server first stored them) - which includes creating one beyond "
                    + "the slots - or left a workspace's groups or links, or a group's subgroups, "
                    + "above their cap and more numerous than before. Deleting a profile, or a "
                    + "workspace of a slot-holding profile, is always allowed. Closed-tab history "
                    + "never refuses; its oldest entries beyond the plan's limit are deleted, never "
                    + "more than the batch added.")
    @ApiResponse(responseCode = "200", description = "The batch was applied")
    @ApiResponse(responseCode = "400", description = "The batch was not well formed",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "The batch wrote into a profile or workspace without a slot, or grew a container past its cap; nothing was applied. Limit PROFILES, WORKSPACES_PER_PROFILE, GROUPS_PER_WORKSPACE, SUBGROUPS_PER_GROUP or LINKS_PER_WORKSPACE. "
            + "Code FREE_PLAN_LIMIT_REACHED (a free account, and upgrading would allow it) or "
            + "FAIR_USE_LIMIT_REACHED, with limit, maximum and manageUrl (the website's "
            + "devices page)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public SyncPushResultDto pushMyChanges(
            @Valid @RequestBody final SyncPushRequestDto pushRequest) {

        return syncPushService.applyPushedOperations(
                pushRequest, authenticatedUserProvider.getAuthenticatedUserId());
    }
}
