package com.kovospace.newtablinks.sync.models;

import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncIdentifierRemapDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What one push knows while it is being applied: who it belongs to, and which identifiers moved.
 *
 * <p>The identifier table is not bookkeeping for the response alone. Operations arrive
 * parent-before-child, so a group that had to be stored under a server-chosen identifier is
 * immediately followed by links naming the identifier the <em>client</em> used. Translating every
 * reference through this table is what stops those links from being rejected as orphans, and what
 * makes replaying the same batch twice land on the same rows.</p>
 *
 * <p>Not thread safe, and never shared: one instance belongs to one request.</p>
 *
 * @since 0.0.6
 */
public class SyncOperationContext {

    private final UUID ownerId;
    private final Map<UUID, UUID> serverIdentifiersByClientIdentifier = new HashMap<>();
    private final List<SyncIdentifierRemapDto> remaps = new ArrayList<>();

    /**
     * Creates the context for one push.
     *
     * @param ownerId identifier of the user the push is authenticated as
     */
    public SyncOperationContext(final UUID ownerId) {
        this.ownerId = ownerId;
    }

    /**
     * Returns the user everything in this push belongs to.
     *
     * @return the owner's identifier, taken from the access token and never from the body
     */
    public UUID getOwnerId() {
        return ownerId;
    }

    /**
     * Notes that a record could not be stored under the identifier the client asked for.
     *
     * <p>Does nothing when the identifier was kept, and nothing when the same client identifier
     * has already been remapped earlier in this push, so a batch that mentions one record twice
     * still produces one entry.</p>
     *
     * @param entityKind       what kind of record it is
     * @param clientIdentifier identifier the client asked for
     * @param serverIdentifier identifier the record was stored under
     */
    public void recordRemapping(
            final SyncEntityKind entityKind,
            final UUID clientIdentifier,
            final UUID serverIdentifier) {

        if (clientIdentifier == null
                || serverIdentifier == null
                || serverIdentifier.equals(clientIdentifier)
                || serverIdentifiersByClientIdentifier.containsKey(clientIdentifier)) {
            return;
        }
        serverIdentifiersByClientIdentifier.put(clientIdentifier, serverIdentifier);
        remaps.add(new SyncIdentifierRemapDto(entityKind, clientIdentifier, serverIdentifier));
    }

    /**
     * Translates an identifier the client used into the one the server actually stored.
     *
     * @param clientIdentifier identifier as it appears in the operation, may be {@code null}
     * @return the server's identifier when this one was remapped earlier in the same push,
     *         otherwise the value that was passed in
     */
    public UUID resolveStoredIdentifier(final UUID clientIdentifier) {
        return serverIdentifiersByClientIdentifier.getOrDefault(clientIdentifier, clientIdentifier);
    }

    /**
     * Returns every remapping this push produced, in the order they happened.
     *
     * @return an unmodifiable view, empty when every identifier was kept
     */
    public List<SyncIdentifierRemapDto> getRemappings() {
        return Collections.unmodifiableList(remaps);
    }
}
