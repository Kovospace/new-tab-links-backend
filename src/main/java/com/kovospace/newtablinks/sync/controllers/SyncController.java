package com.kovospace.newtablinks.sync.controllers;

import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.sync.dtos.SyncSnapshotDto;
import com.kovospace.newtablinks.sync.services.SyncSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final AuthenticatedUserProvider authenticatedUserProvider;

    /**
     * Creates the controller.
     *
     * @param syncSnapshotService       service assembling the snapshot
     * @param authenticatedUserProvider identifies the user the request is authenticated as
     */
    public SyncController(
            final SyncSnapshotService syncSnapshotService,
            final AuthenticatedUserProvider authenticatedUserProvider) {

        this.syncSnapshotService = syncSnapshotService;
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
}
