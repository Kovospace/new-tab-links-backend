package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.group.services.GroupSynchronizationService;
import com.kovospace.newtablinks.link.dtos.LinkSynchronizedValuesDto;
import com.kovospace.newtablinks.link.services.LinkSynchronizationService;
import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
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
 * Applies pushed operations about links.
 *
 * <p>The only kind with two parents. The group is required; the subgroup is optional and a link
 * that names none sits directly under its group, which is exactly how the rest of the application
 * reads a {@code null} subgroup.</p>
 *
 * @since 0.0.6
 */
@Component
public class LinkSyncOperationApplier implements SyncOperationApplier {

    private final LinkSynchronizationService linkSynchronizationService;
    private final GroupSynchronizationService groupSynchronizationService;
    private final SubgroupSynchronizationService subgroupSynchronizationService;

    /**
     * Creates the applier.
     *
     * @param linkSynchronizationService     stores links the client pushed
     * @param groupSynchronizationService    resolves the parent group without throwing
     * @param subgroupSynchronizationService resolves the optional parent subgroup without throwing
     */
    public LinkSyncOperationApplier(
            final LinkSynchronizationService linkSynchronizationService,
            final GroupSynchronizationService groupSynchronizationService,
            final SubgroupSynchronizationService subgroupSynchronizationService) {

        this.linkSynchronizationService = linkSynchronizationService;
        this.groupSynchronizationService = groupSynchronizationService;
        this.subgroupSynchronizationService = subgroupSynchronizationService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncEntityKind supportedEntityKind() {
        return SyncEntityKind.LINK;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID applyUpsert(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        final GroupEntity parentGroup = resolveParentGroup(operation, context);
        final SubgroupEntity parentSubgroup = resolveOptionalParentSubgroup(operation, context);

        final LinkSynchronizedValuesDto values = new LinkSynchronizedValuesDto(
                PushedOperationValues.requireSuppliedText(operation.title()),
                PushedOperationValues.requireSuppliedText(operation.url()),
                operation.faviconUrl(),
                PushedOperationValues.requireSuppliedPosition(operation.position()));

        context.recordWriteIntoWorkspace(parentGroup.getEnvironment().getId());
        return linkSynchronizationService.upsertLinkFromPushedOperation(
                        context.resolveStoredIdentifier(operation.id()),
                        parentGroup,
                        parentSubgroup,
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

        final UUID storedLinkId = context.resolveStoredIdentifier(operation.id());
        linkSynchronizationService.findLinkEntityForOwner(storedLinkId, context.getOwnerId())
                .ifPresent(link -> context.recordWriteIntoWorkspace(
                        link.getParentGroup().getEnvironment().getId()));
        linkSynchronizationService.deleteLinkFromPushedOperationIfPresent(
                storedLinkId, context.getOwnerId());
    }

    /**
     * Resolves the group the link belongs to.
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

    /**
     * Resolves the subgroup the link is nested in, when it names one.
     *
     * @param operation the operation being applied
     * @param context   the push this operation is part of
     * @return the managed subgroup, or {@code null} when the link sits directly in its group
     * @throws SyncOperationRejectedException when a subgroup was named but is not the caller's
     */
    private SubgroupEntity resolveOptionalParentSubgroup(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        if (operation.parentSubgroupId() == null) {
            return null;
        }
        final UUID parentSubgroupId =
                context.resolveStoredIdentifier(operation.parentSubgroupId());

        return subgroupSynchronizationService
                .findSubgroupEntityForOwner(parentSubgroupId, context.getOwnerId())
                .orElseThrow(() ->
                        new SyncOperationRejectedException(SyncRejectionReason.PARENT_NOT_FOUND));
    }
}
