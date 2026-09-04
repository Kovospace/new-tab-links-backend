package com.kovospace.newtablinks.sync.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;

/**
 * What a pushed synchronization operation does to the record it names.
 *
 * <p>There is no separate "create": a client that has been offline cannot know whether the server
 * has already seen a record, so every write is an upsert. See {@link SyncEntityKind} for why the
 * wire form is spelled out.</p>
 *
 * @since 0.0.6
 */
@Schema(description = "What an operation does to the record it names",
        allowableValues = {"upsert", "delete"})
public enum SyncOperationKind {

    /** Store the record, updating it when the owner already has one under that identifier. */
    UPSERT,

    /** Remove the record, doing nothing when the owner no longer has one. */
    DELETE;

    /**
     * Returns the name this operation travels under.
     *
     * @return the lower case wire name
     */
    @JsonValue
    public String getWireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a wire name back into an operation, accepting either case.
     *
     * @param wireName the value read from the request body
     * @return the matching operation
     * @throws IllegalArgumentException when no operation has that name, which the request
     *                                  validation turns into a 400
     */
    @JsonCreator
    public static SyncOperationKind fromWireName(final String wireName) {
        return valueOf(wireName.toUpperCase(Locale.ROOT));
    }
}
