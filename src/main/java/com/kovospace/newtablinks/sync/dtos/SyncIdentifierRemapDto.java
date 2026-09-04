package com.kovospace.newtablinks.sync.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Tells a client that one of its identifiers could not be kept, and what replaced it.
 *
 * <p>Emitted only when the identifier the client asked for was already in use. The client applies
 * the replacement to its own copy and to every reference pointing at it; nothing is said about
 * why the identifier was refused, because the only reason is that it belongs to another account.
 * </p>
 *
 * @param entityKind what kind of record was remapped
 * @param clientId   identifier the client asked for
 * @param serverId   identifier the record was actually stored under
 * @since 0.0.6
 */
@Schema(description = "An identifier the server could not keep, and what replaced it")
public record SyncIdentifierRemapDto(

        @Schema(description = "What kind of record was remapped", example = "link")
        @JsonProperty("type") SyncEntityKind entityKind,

        @Schema(description = "Identifier the client asked for") UUID clientId,

        @Schema(description = "Identifier the record was stored under") UUID serverId) {
}
