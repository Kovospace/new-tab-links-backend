package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.exceptions.SyncOperationRejectedException;
import com.kovospace.newtablinks.sync.models.SyncOperationContext;
import java.util.UUID;

/**
 * Applies the pushed operations of one kind of record.
 *
 * <p>One implementation per {@link SyncEntityKind}, discovered by Spring and indexed by
 * {@link SyncPushService}. A sixth kind of record is therefore a sixth class rather than a sixth
 * branch in a growing switch.</p>
 *
 * <p>Implementations resolve their parents through the owning module's <em>service</em>, never
 * through its repository, and only through lookups that return an empty result rather than
 * throwing - see {@link SyncOperationRejectedException} for why that distinction is not
 * stylistic.</p>
 *
 * @since 0.0.6
 */
public interface SyncOperationApplier {

    /**
     * Returns the kind of record this applier is responsible for.
     *
     * @return the entity kind
     */
    SyncEntityKind supportedEntityKind();

    /**
     * Stores the record the operation describes.
     *
     * @param operation the operation to apply
     * @param context   the push this operation is part of
     * @return the identifier the record was actually stored under, which differs from the one in
     *         the operation exactly when that one was already taken
     * @throws SyncOperationRejectedException when the operation cannot be applied at all
     */
    UUID applyUpsert(SyncOperationDto operation, SyncOperationContext context);

    /**
     * Removes the record the operation names, doing nothing when it is already gone.
     *
     * @param operation the operation to apply
     * @param context   the push this operation is part of
     * @throws SyncOperationRejectedException when the operation cannot be applied at all
     */
    void applyDelete(SyncOperationDto operation, SyncOperationContext context);
}
