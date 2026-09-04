package com.kovospace.newtablinks.sync.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * One operation of a push that was not applied, and why.
 *
 * <p>A rejection does not fail the push. The operations around it still apply, so a client whose
 * queue contains one change referring to something it has since deleted does not lose the rest of
 * its offline work.</p>
 *
 * @param index      position of the operation in the request array, so the client can find it
 * @param entityKind what kind of record it was about
 * @param id         identifier the client used for the record
 * @param reason     why it was not applied
 * @since 0.0.6
 */
@Schema(description = "An operation that was not applied, and why")
public record SyncRejectedOperationDto(

        @Schema(description = "Position of the operation in the request array", example = "3")
        int index,

        @Schema(description = "What kind of record it was about", example = "link")
        @JsonProperty("type") SyncEntityKind entityKind,

        @Schema(description = "Identifier the client used for the record") UUID id,

        @Schema(description = "Why it was not applied", example = "PARENT_NOT_FOUND")
        SyncRejectionReason reason) {
}
