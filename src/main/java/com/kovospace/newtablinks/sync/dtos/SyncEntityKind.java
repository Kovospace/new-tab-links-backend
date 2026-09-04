package com.kovospace.newtablinks.sync.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;

/**
 * The kind of record a pushed synchronization operation is about.
 *
 * <p>The wire form is lower case, matching the names the browser extension uses for the same
 * things locally. It is spelled out here rather than left to Jackson's default enum handling,
 * because the contract is shared with a client written in another repository and a rename on this
 * side would silently stop matching.</p>
 *
 * @since 0.0.6
 */
@Schema(description = "The kind of record an operation is about",
        allowableValues = {"profile", "environment", "group", "subgroup", "link"})
public enum SyncEntityKind {

    /** A profile: the top of the hierarchy. */
    PROFILE,

    /** An environment inside a profile. */
    ENVIRONMENT,

    /** A group inside an environment. */
    GROUP,

    /** A subgroup inside a group. */
    SUBGROUP,

    /** A link inside a group, optionally nested in one of its subgroups. */
    LINK;

    /**
     * Returns the name this kind travels under.
     *
     * @return the lower case wire name
     */
    @JsonValue
    public String getWireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a wire name back into a kind, accepting either case.
     *
     * @param wireName the value read from the request body
     * @return the matching kind
     * @throws IllegalArgumentException when no kind has that name, which the request validation
     *                                  turns into a 400
     */
    @JsonCreator
    public static SyncEntityKind fromWireName(final String wireName) {
        return valueOf(wireName.toUpperCase(Locale.ROOT));
    }
}
