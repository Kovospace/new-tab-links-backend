package com.kovospace.newtablinks.common.utils;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Decides which identifier a row pushed by a client is inserted under.
 *
 * <p>Synchronization is the only place in this application where the caller names the row. The
 * browser extension works offline, mints its own UUIDs, and has already wired them into its local
 * references, so a pushed change must keep the identifier it arrived with wherever that is
 * possible.</p>
 *
 * <p>It is not always possible. A version 4 UUID carries 122 random bits, so two clients
 * colliding by accident is not a real risk, but a client can reach a taken identifier by
 * ordinary means - exporting a profile and importing it into a second account, for instance.
 * When the identifier is already in use the row is inserted under a server-chosen one instead
 * and the client is told, rather than the push failing. It is never revealed <em>why</em> the
 * identifier was refused, because the only reason is that it belongs to somebody else and saying
 * so would confirm that another account's row exists.</p>
 *
 * @since 0.0.6
 */
public final class ClientAssignedIdentifierPolicy {

    /**
     * Not instantiable; this class only holds static helpers.
     */
    private ClientAssignedIdentifierPolicy() {
        throw new AssertionError(
                "ClientAssignedIdentifierPolicy is a utility class and must not be instantiated");
    }

    /**
     * Chooses the identifier to insert a client-supplied row under.
     *
     * @param clientIdentifier    identifier the client asked for, may be {@code null}
     * @param isIdentifierTaken   tells whether an identifier is already used by any row of the
     *                            same kind, regardless of who owns it
     * @return {@code clientIdentifier} when it was supplied and is free, otherwise a fresh random
     *         UUID that the caller must report back to the client as a remapping
     */
    public static UUID chooseIdentifierForInsert(
            final UUID clientIdentifier,
            final Predicate<UUID> isIdentifierTaken) {

        if (clientIdentifier == null || isIdentifierTaken.test(clientIdentifier)) {
            return UUID.randomUUID();
        }
        return clientIdentifier;
    }
}
