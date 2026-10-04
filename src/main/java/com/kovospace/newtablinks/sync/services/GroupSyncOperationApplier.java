package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import com.kovospace.newtablinks.environment.services.EnvironmentSynchronizationService;
import com.kovospace.newtablinks.group.dtos.GroupSynchronizedValuesDto;
import com.kovospace.newtablinks.group.services.GroupSynchronizationService;
import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.dtos.SyncRejectionReason;
import com.kovospace.newtablinks.sync.exceptions.SyncOperationRejectedException;
import com.kovospace.newtablinks.sync.models.SyncOperationContext;
import com.kovospace.newtablinks.sync.utils.PushedOperationValues;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Applies pushed operations about groups.
 *
 * @since 0.0.6
 */
@Component
public class GroupSyncOperationApplier implements SyncOperationApplier {

    private final GroupSynchronizationService groupSynchronizationService;
    private final EnvironmentSynchronizationService environmentSynchronizationService;

    /**
     * Creates the applier.
     *
     * @param groupSynchronizationService       stores groups the client pushed
     * @param environmentSynchronizationService resolves the parent environment without throwing
     */
    public GroupSyncOperationApplier(
            final GroupSynchronizationService groupSynchronizationService,
            final EnvironmentSynchronizationService environmentSynchronizationService) {

        this.groupSynchronizationService = groupSynchronizationService;
        this.environmentSynchronizationService = environmentSynchronizationService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncEntityKind supportedEntityKind() {
        return SyncEntityKind.GROUP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID applyUpsert(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        final EnvironmentEntity parentEnvironment = resolveParentEnvironment(operation, context);

        final GroupSynchronizedValuesDto values = new GroupSynchronizedValuesDto(
                PushedOperationValues.requireSuppliedText(operation.name()),
                operation.description(),
                PushedOperationValues.requireSuppliedPosition(operation.position()));

        context.recordWriteIntoWorkspace(parentEnvironment.getId());
        return groupSynchronizationService.upsertGroupFromPushedOperation(
                        context.resolveStoredIdentifier(operation.id()), parentEnvironment, values)
                .getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyDelete(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        final UUID storedGroupId = context.resolveStoredIdentifier(operation.id());
        groupSynchronizationService.findGroupEntityForOwner(storedGroupId, context.getOwnerId())
                .ifPresent(group ->
                        context.recordWriteIntoWorkspace(group.getEnvironment().getId()));
        groupSynchronizationService.deleteGroupFromPushedOperationIfPresent(
                storedGroupId, context.getOwnerId());
    }

    /**
     * Resolves the environment the group is displayed in.
     *
     * @param operation the operation being applied
     * @param context   the push this operation is part of
     * @return the managed environment
     * @throws SyncOperationRejectedException when no environment was named, or the named one is
     *                                        not the caller's
     */
    private EnvironmentEntity resolveParentEnvironment(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        final UUID parentEnvironmentId = context.resolveStoredIdentifier(
                PushedOperationValues.requireSuppliedIdentifier(operation.environmentId()));

        return environmentSynchronizationService
                .findEnvironmentEntityForOwner(parentEnvironmentId, context.getOwnerId())
                .orElseThrow(() ->
                        new SyncOperationRejectedException(SyncRejectionReason.PARENT_NOT_FOUND));
    }
}
