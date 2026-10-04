package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.environment.dtos.EnvironmentSynchronizedValuesDto;
import com.kovospace.newtablinks.environment.services.EnvironmentSynchronizationService;
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
 * Applies pushed operations about environments.
 *
 * @since 0.0.6
 */
@Component
public class EnvironmentSyncOperationApplier implements SyncOperationApplier {

    private final EnvironmentSynchronizationService environmentSynchronizationService;
    private final ProfileSynchronizationService profileSynchronizationService;

    /**
     * Creates the applier.
     *
     * @param environmentSynchronizationService stores environments the client pushed
     * @param profileSynchronizationService     resolves the parent profile without throwing
     */
    public EnvironmentSyncOperationApplier(
            final EnvironmentSynchronizationService environmentSynchronizationService,
            final ProfileSynchronizationService profileSynchronizationService) {

        this.environmentSynchronizationService = environmentSynchronizationService;
        this.profileSynchronizationService = profileSynchronizationService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncEntityKind supportedEntityKind() {
        return SyncEntityKind.ENVIRONMENT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID applyUpsert(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        final ProfileEntity parentProfile = resolveParentProfile(operation, context);

        final EnvironmentSynchronizedValuesDto values = new EnvironmentSynchronizedValuesDto(
                PushedOperationValues.requireSuppliedText(operation.name()),
                operation.description(),
                PushedOperationValues.requireSuppliedPosition(operation.position()));

        final UUID storedEnvironmentId = environmentSynchronizationService
                .upsertEnvironmentFromPushedOperation(
                        context.resolveStoredIdentifier(operation.id()), parentProfile, values)
                .getId();
        context.recordWriteIntoWorkspace(storedEnvironmentId);
        return storedEnvironmentId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyDelete(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        final UUID storedEnvironmentId = context.resolveStoredIdentifier(operation.id());
        environmentSynchronizationService
                .findEnvironmentEntityForOwner(storedEnvironmentId, context.getOwnerId())
                .ifPresent(environment ->
                        context.recordWriteIntoProfile(environment.getProfile().getId()));
        environmentSynchronizationService.deleteEnvironmentFromPushedOperationIfPresent(
                storedEnvironmentId, context.getOwnerId());
    }

    /**
     * Resolves the profile the environment is filed under.
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
