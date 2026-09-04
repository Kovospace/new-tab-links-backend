package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.profile.dtos.ProfileSynchronizedValuesDto;
import com.kovospace.newtablinks.profile.services.ProfileSynchronizationService;
import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.models.SyncOperationContext;
import com.kovospace.newtablinks.sync.utils.PushedOperationValues;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Applies pushed operations about profiles.
 *
 * <p>The only kind with no parent to resolve, so the only kind that cannot be rejected for an
 * orphaned reference.</p>
 *
 * @since 0.0.6
 */
@Component
public class ProfileSyncOperationApplier implements SyncOperationApplier {

    private final ProfileSynchronizationService profileSynchronizationService;

    /**
     * Creates the applier.
     *
     * @param profileSynchronizationService stores profiles the client pushed
     */
    public ProfileSyncOperationApplier(
            final ProfileSynchronizationService profileSynchronizationService) {

        this.profileSynchronizationService = profileSynchronizationService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncEntityKind supportedEntityKind() {
        return SyncEntityKind.PROFILE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID applyUpsert(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        final ProfileSynchronizedValuesDto values = new ProfileSynchronizedValuesDto(
                PushedOperationValues.requireSuppliedText(operation.name()),
                PushedOperationValues.requireSuppliedPosition(operation.position()));

        return profileSynchronizationService.upsertProfileFromPushedOperation(
                        context.resolveStoredIdentifier(operation.id()),
                        context.getOwnerId(),
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

        profileSynchronizationService.deleteProfileFromPushedOperationIfPresent(
                context.resolveStoredIdentifier(operation.id()), context.getOwnerId());
    }
}
