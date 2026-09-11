package com.kovospace.newtablinks.sync.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;

/**
 * The kind of record a pushed synchronization operation is about.
 *
 * <p>The wire form matches the name the browser extension uses for the same thing locally. It is
 * spelled out here rather than left to Jackson's default enum handling, because the contract is
 * shared with a client written in another repository and a rename on this side would silently
 * stop matching.</p>
 *
 * <p>Each constant carries its wire name explicitly rather than deriving it. The first five are
 * their own names lowercased, but {@link #CLOSED_TAB} is not: the extension spells it
 * {@code closedTab}, in the camel case JavaScript writes every identifier in, and a derived form
 * would have been {@code closed_tab} and matched nothing.</p>
 *
 * @since 0.0.6
 */
@Schema(description = "The kind of record an operation is about",
        allowableValues = {"profile", "environment", "group", "subgroup", "link", "closedTab"})
public enum SyncEntityKind {

    /** A profile: the top of the hierarchy. */
    PROFILE("profile"),

    /** An environment inside a profile. */
    ENVIRONMENT("environment"),

    /** A group inside an environment. */
    GROUP("group"),

    /** A subgroup inside a group. */
    SUBGROUP("subgroup"),

    /** A link inside a group, optionally nested in one of its subgroups. */
    LINK("link"),

    /** A tab the user closed, on the list of one profile. */
    CLOSED_TAB("closedTab");

    /** The name this kind travels under, exactly as the extension spells it. */
    private final String wireName;

    /**
     * Creates a kind.
     *
     * @param wireName the name this kind travels under
     */
    SyncEntityKind(final String wireName) {
        this.wireName = wireName;
    }

    /**
     * Returns the name this kind travels under.
     *
     * @return the wire name
     */
    @JsonValue
    public String getWireName() {
        return wireName;
    }

    /**
     * Parses a wire name back into a kind, accepting any casing of it.
     *
     * <p>Casing is ignored for the reason it always was: a client that spells {@code closedtab}
     * or {@code CLOSEDTAB} is understood rather than failing a whole batch over a letter.</p>
     *
     * @param wireName the value read from the request body
     * @return the matching kind
     * @throws IllegalArgumentException when no kind travels under that name, which the request
     *                                  validation turns into a 400
     */
    @JsonCreator
    public static SyncEntityKind fromWireName(final String wireName) {
        return Arrays.stream(values())
                .filter(kind -> kind.wireName.equalsIgnoreCase(wireName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No synchronizable entity kind travels under the name " + wireName));
    }
}
