package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.services.ProfileSynchronizationService;
import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.dtos.SyncRejectionReason;
import com.kovospace.newtablinks.sync.exceptions.SyncOperationRejectedException;
import com.kovospace.newtablinks.sync.models.SyncOperationContext;
import com.kovospace.newtablinks.sync.utils.PushedOperationValues;
import com.kovospace.newtablinks.visit.dtos.VisitSynchronizedValuesDto;
import com.kovospace.newtablinks.visit.services.VisitSynchronizationService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Applies pushed operations about visits - the profile's browsing history.
 *
 * <p>Built exactly like {@link ClosedTabSyncOperationApplier}: the parent is the profile itself,
 * there is no display position, and deletes are ordinary traffic because the extension keeps a
 * rolling list and pushes what it drops.</p>
 *
 * @since 0.0.19
 */
@Component
public class VisitSyncOperationApplier implements SyncOperationApplier {

    private final VisitSynchronizationService visitSynchronizationService;
    private final ProfileSynchronizationService profileSynchronizationService;

    /**
     * Creates the applier.
     *
     * @param visitSynchronizationService   stores visits the client pushed
     * @param profileSynchronizationService resolves the parent profile without throwing
     */
    public VisitSyncOperationApplier(
            final VisitSynchronizationService visitSynchronizationService,
            final ProfileSynchronizationService profileSynchronizationService) {

        this.visitSynchronizationService = visitSynchronizationService;
        this.profileSynchronizationService = profileSynchronizationService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncEntityKind supportedEntityKind() {
        return SyncEntityKind.VISIT;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The address and the visiting moment are required; the title, the favicon and the device
     * name are not. An absent title is stored as empty, while an absent visiting moment is
     * refused rather than replaced with this server's clock - see
     * {@link PushedOperationValues#requireSuppliedMoment(java.time.Instant)}.</p>
     */
    @Override
    public UUID applyUpsert(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        final ProfileEntity parentProfile = resolveParentProfile(operation, context);

        final VisitSynchronizedValuesDto values = new VisitSynchronizedValuesDto(
                PushedOperationValues.requireSuppliedText(operation.url()),
                PushedOperationValues.textOrEmpty(operation.title()),
                operation.faviconUrl(),
                PushedOperationValues.requireSuppliedMoment(operation.visitedAt()),
                operation.deviceName());

        return visitSynchronizationService.upsertVisitFromPushedOperation(
                        context.resolveStoredIdentifier(operation.id()),
                        parentProfile,
                        values)
                .getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyDelete(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        visitSynchronizationService.deleteVisitFromPushedOperationIfPresent(
                context.resolveStoredIdentifier(operation.id()), context.getOwnerId());
    }

    /**
     * Resolves the profile whose history the visit is in.
     *
     * @param operation the operation being applied
     * @param context   the push this operation is part of
     * @return the managed profile
     * @throws SyncOperationRejectedException when no profile was named, or the named one is not
     *                                        the caller's
     */
    private ProfileEntity resolveParentProfile(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        final UUID parentProfileId = context.resolveStoredIdentifier(
                PushedOperationValues.requireSuppliedIdentifier(operation.profileId()));

        return profileSynchronizationService
                .findProfileEntityForOwner(parentProfileId, context.getOwnerId())
                .orElseThrow(() ->
                        new SyncOperationRejectedException(SyncRejectionReason.PARENT_NOT_FOUND));
    }
}
