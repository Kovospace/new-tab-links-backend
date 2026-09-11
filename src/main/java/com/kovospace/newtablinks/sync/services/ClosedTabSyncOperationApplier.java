package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.closedtab.dtos.ClosedTabSynchronizedValuesDto;
import com.kovospace.newtablinks.closedtab.services.ClosedTabSynchronizationService;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.services.ProfileSynchronizationService;
import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.dtos.SyncRejectionReason;
import com.kovospace.newtablinks.sync.exceptions.SyncOperationRejectedException;
import com.kovospace.newtablinks.sync.models.SyncOperationContext;
import com.kovospace.newtablinks.sync.utils.PushedOperationValues;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Applies pushed operations about closed tabs.
 *
 * <p>The first kind whose parent is the profile itself rather than something inside it, and the
 * first with no display position: a closed tab is ordered by when it was closed, which is a fact
 * about the past and not an arrangement the user can change.</p>
 *
 * <p>Deletes are ordinary traffic here, not an exception. The extension keeps a capped list per
 * profile and pushes every entry it prunes as a delete, so this applier sees far more of them
 * than any other.</p>
 *
 * @since 0.0.8
 */
@Component
public class ClosedTabSyncOperationApplier implements SyncOperationApplier {

    private final ClosedTabSynchronizationService closedTabSynchronizationService;
    private final ProfileSynchronizationService profileSynchronizationService;

    /**
     * Creates the applier.
     *
     * @param closedTabSynchronizationService stores closed tabs the client pushed
     * @param profileSynchronizationService   resolves the parent profile without throwing
     */
    public ClosedTabSyncOperationApplier(
            final ClosedTabSynchronizationService closedTabSynchronizationService,
            final ProfileSynchronizationService profileSynchronizationService) {

        this.closedTabSynchronizationService = closedTabSynchronizationService;
        this.profileSynchronizationService = profileSynchronizationService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncEntityKind supportedEntityKind() {
        return SyncEntityKind.CLOSED_TAB;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The address and the closing moment are required; the title, the favicon and the device
     * name are not. An absent title is stored as empty, because a page that never named itself is
     * an ordinary page, while an absent closing moment is refused rather than replaced with this
     * server's clock - see
     * {@link PushedOperationValues#requireSuppliedMoment(java.time.Instant)}.</p>
     */
    @Override
    public UUID applyUpsert(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        final ProfileEntity parentProfile = resolveParentProfile(operation, context);

        final ClosedTabSynchronizedValuesDto values = new ClosedTabSynchronizedValuesDto(
                PushedOperationValues.requireSuppliedText(operation.url()),
                PushedOperationValues.textOrEmpty(operation.title()),
                operation.faviconUrl(),
                PushedOperationValues.requireSuppliedMoment(operation.closedAt()),
                operation.deviceName());

        return closedTabSynchronizationService.upsertClosedTabFromPushedOperation(
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

        closedTabSynchronizationService.deleteClosedTabFromPushedOperationIfPresent(
                context.resolveStoredIdentifier(operation.id()), context.getOwnerId());
    }

    /**
     * Resolves the profile whose list the closed tab is on.
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
