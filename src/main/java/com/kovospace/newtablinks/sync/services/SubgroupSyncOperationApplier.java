package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.group.services.GroupSynchronizationService;
import com.kovospace.newtablinks.subgroup.dtos.SubgroupSynchronizedValuesDto;
import com.kovospace.newtablinks.subgroup.models.SubgroupCollapseState;
import com.kovospace.newtablinks.subgroup.services.SubgroupSynchronizationService;
import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.dtos.SyncRejectionReason;
import com.kovospace.newtablinks.sync.exceptions.SyncOperationRejectedException;
import com.kovospace.newtablinks.sync.models.SyncOperationContext;
import com.kovospace.newtablinks.sync.utils.PushedOperationValues;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Applies pushed operations about subgroups.
 *
 * @since 0.0.6
 */
@Component
public class SubgroupSyncOperationApplier implements SyncOperationApplier {

    private final SubgroupSynchronizationService subgroupSynchronizationService;
    private final GroupSynchronizationService groupSynchronizationService;

    /**
     * Creates the applier.
     *
     * @param subgroupSynchronizationService stores subgroups the client pushed
     * @param groupSynchronizationService    resolves the parent group without throwing
     */
    public SubgroupSyncOperationApplier(
            final SubgroupSynchronizationService subgroupSynchronizationService,
            final GroupSynchronizationService groupSynchronizationService) {

        this.subgroupSynchronizationService = subgroupSynchronizationService;
        this.groupSynchronizationService = groupSynchronizationService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncEntityKind supportedEntityKind() {
        return SyncEntityKind.SUBGROUP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID applyUpsert(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        final GroupEntity parentGroup = resolveParentGroup(operation, context);

        final SubgroupSynchronizedValuesDto values = new SubgroupSynchronizedValuesDto(
                PushedOperationValues.requireSuppliedText(operation.name()),
                operation.description(),
                new SubgroupCollapseState(
                        PushedOperationValues.flagOrFalse(operation.collapsed()),
                        PushedOperationValues.flagOrFalse(operation.defaultCollapsed())),
                PushedOperationValues.flagOrFalse(operation.catchLinksIntoTabGroup()),
                operation.color(),
                PushedOperationValues.requireSuppliedPosition(operation.position()));

        return subgroupSynchronizationService.upsertSubgroupFromPushedOperation(
                        context.resolveStoredIdentifier(operation.id()), parentGroup, values)
                .getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyDelete(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        subgroupSynchronizationService.deleteSubgroupFromPushedOperationIfPresent(
                context.resolveStoredIdentifier(operation.id()), context.getOwnerId());
    }

    /**
     * Resolves the group the subgroup is nested in.
     *
     * @param operation the operation being applied
     * @param context   the push this operation is part of
     * @return the managed group
     * @throws SyncOperationRejectedException when no group was named, or the named one is not the
     *                                        caller's
     */
    private GroupEntity resolveParentGroup(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        final UUID parentGroupId = context.resolveStoredIdentifier(
                PushedOperationValues.requireSuppliedIdentifier(operation.parentGroupId()));

        return groupSynchronizationService
                .findGroupEntityForOwner(parentGroupId, context.getOwnerId())
                .orElseThrow(() ->
                        new SyncOperationRejectedException(SyncRejectionReason.PARENT_NOT_FOUND));
    }
}
