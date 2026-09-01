package com.kovospace.newtablinks.auth.utils;

/**
 * The single definition of what counts as a well-formed username.
 *
 * <p>Two endpoints validate a username: registration, which stores it, and the existence lookup
 * the website's form calls while the user types. They must agree exactly - a lookup that accepts
 * a name registration would reject tells the user the name is free when it can never be theirs,
 * and one that rejects a name registration accepts silently breaks the form. Stating the rules
 * once is what keeps that from drifting.</p>
 *
 * <p>The values are compile-time constants because bean validation annotations can only take
 * those; they cannot be made configurable without changing every stored username's validity.</p>
 *
 * @since 0.0.4
 */
public final class UsernameConstraints {

    /**
     * Shortest accepted username.
     */
    public static final int MINIMUM_LENGTH = 3;

    /**
     * Longest accepted username, matching the column it is stored in.
     */
    public static final int MAXIMUM_LENGTH = 60;

    /**
     * Characters a username may consist of: letters, digits, dot, underscore and hyphen.
     */
    public static final String ALLOWED_CHARACTERS_PATTERN = "^[A-Za-z0-9._-]+$";

    /**
     * Message shown when a username contains anything outside
     * {@link #ALLOWED_CHARACTERS_PATTERN}.
     */
    public static final String ALLOWED_CHARACTERS_MESSAGE =
            "may contain only letters, digits, dot, underscore and hyphen";

    /**
     * Prevents instantiation of this constant holder.
     */
    private UsernameConstraints() {
    }
}
