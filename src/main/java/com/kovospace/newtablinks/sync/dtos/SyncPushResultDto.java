package com.kovospace.newtablinks.sync.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * What became of a pushed batch.
 *
 * <p>A push answers 200 whenever it was understood, even when parts of it were refused: the
 * operations are independent, and failing all of them because one named a vanished parent would
 * strand a device that has been offline. {@link #rejected()} is how the client learns which ones
 * to reconcile.</p>
 *
 * @param appliedAt moment the batch was applied
 * @param remaps    identifiers the server could not keep, empty when it kept them all
 * @param rejected  operations that were not applied, empty when all of them were
 * @since 0.0.6
 */
@Schema(description = "What became of a pushed batch")
public record SyncPushResultDto(

        @Schema(description = "Moment the batch was applied", example = "2026-09-04T10:15:30Z")
        Instant appliedAt,

        @Schema(description = "Identifiers the server could not keep")
        List<SyncIdentifierRemapDto> remaps,

        @Schema(description = "Operations that were not applied")
        List<SyncRejectedOperationDto> rejected) {
}
