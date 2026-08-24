package com.kovospace.newtablinks.sync.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Pushed to a user's connected browsers when their stored data has changed.
 *
 * <p>Carries no data of its own on purpose. Sending the change itself would mean designing a
 * patch format, guaranteeing ordering, and handling a client that missed one - all of which the
 * snapshot endpoint already solves. This says only "you are out of date"; the client then pulls
 * {@code GET /api/v1/sync/snapshot} when it is ready to.</p>
 *
 * @param changedAt moment the change was committed
 * @param origin    description of the device that caused it, so a browser can recognise and
 *                  ignore an echo of its own change
 * @since 0.0.3
 */
@Schema(description = "Tells a connected browser that the account's data has changed")
public record DataChangedNotificationDto(

        @Schema(description = "Moment the change was committed")
        Instant changedAt,

        @Schema(description = "Device that caused the change", example = "Work laptop / Firefox")
        String origin) {
}
