package com.kovospace.newtablinks.sync.controllers;

import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.sync.dtos.SyncSnapshotDto;
import com.kovospace.newtablinks.sync.services.SyncSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /**
     * Creates the controller.
     *
     * @param syncSnapshotService service assembling the snapshot
     */
    public SyncController(final SyncSnapshotService syncSnapshotService) {
        this.syncSnapshotService = syncSnapshotService;
    }

    /**
     * Returns everything one user owns, as flat collections.
     *
     * @param ownerId identifier of the user to snapshot
     * @return the snapshot
     */
    @GetMapping("/{ownerId}/snapshot")
    @Operation(summary = "Return everything one user owns, as flat collections keyed by identifier")
    @ApiResponse(responseCode = "200", description = "The snapshot")
    @ApiResponse(responseCode = "404", description = "No user has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public SyncSnapshotDto getSnapshot(@PathVariable final UUID ownerId) {
        return syncSnapshotService.captureSnapshotForUser(ownerId);
    }
}
