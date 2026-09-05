package com.kovospace.newtablinks.subgroup.services;

import com.kovospace.newtablinks.common.services.HierarchyDeletionService;
import com.kovospace.newtablinks.common.utils.ClientAssignedIdentifierPolicy;
import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.subgroup.dtos.SubgroupSynchronizedValuesDto;
import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
import com.kovospace.newtablinks.subgroup.repositories.SubgroupRepository;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies pushed synchronization operations to subgroups.
 *
 * <p>Separate from {@link SubgroupService} for the reason given on
 * {@link com.kovospace.newtablinks.profile.services.ProfileSynchronizationService}, and nothing
 * here throws when a row is absent, for the reason given there too.</p>
 *
 * <p>The owner is read from the parent group rather than passed in, so a pushed subgroup cannot
 * land under a group the caller does not own.</p>
 *
 * @since 0.0.6
 */
@Service
public class SubgroupSynchronizationService {

    private final SubgroupRepository subgroupRepository;
    private final UserDataChangePublisher userDataChangePublisher;
    private final HierarchyDeletionService hierarchyDeletionService;

    /**
     * Creates the service.
     *
     * @param subgroupRepository       persistence access for subgroups
     * @param userDataChangePublisher  announces changes to the user's other browsers
     * @param hierarchyDeletionService removes a record together with everything beneath it
     */
    public SubgroupSynchronizationService(
            final SubgroupRepository subgroupRepository,
            final UserDataChangePublisher userDataChangePublisher,
            final HierarchyDeletionService hierarchyDeletionService) {

        this.subgroupRepository = subgroupRepository;
        this.userDataChangePublisher = userDataChangePublisher;
        this.hierarchyDeletionService = hierarchyDeletionService;
    }

    /**
     * Looks a subgroup up without throwing when it is absent or somebody else's.
     *
     * @param subgroupId identifier of the subgroup
     * @param ownerId    identifier of the user that must own it
     * @return the managed entity, or an empty optional when it does not exist or is not theirs
     */
    @Transactional(readOnly = true)
    public Optional<SubgroupEntity> findSubgroupEntityForOwner(
            final UUID subgroupId,
            final UUID ownerId) {

        return subgroupRepository.findByIdAndOwnerId(subgroupId, ownerId);
    }

    /**
     * Stores a subgroup the client pushed, updating the owner's existing row or inserting one.
     *
     * @param requestedSubgroupId identifier the client wants the subgroup stored under
     * @param parentGroup         group the subgroup is nested in, already resolved against the
     *                            caller's own identifier
     * @param values              the fields to store, all of which are replaced
     * @return the stored entity, whose identifier differs from the requested one exactly when the
     *         requested one was already taken
     */
    @Transactional
    public SubgroupEntity upsertSubgroupFromPushedOperation(
            final UUID requestedSubgroupId,
            final GroupEntity parentGroup,
            final SubgroupSynchronizedValuesDto values) {

        final UUID ownerId = parentGroup.getEnvironment().getOwner().getId();
        final Optional<SubgroupEntity> existingSubgroup =
                subgroupRepository.findByIdAndOwnerId(requestedSubgroupId, ownerId);

        userDataChangePublisher.publishChangeFor(ownerId);

        if (existingSubgroup.isPresent()) {
            return applyValues(existingSubgroup.get(), parentGroup, values);
        }
        return insertSubgroup(requestedSubgroupId, parentGroup, values);
    }

    /**
     * Deletes a subgroup, and the links nested in it, if the owner still has one under that
     * identifier.
     *
     * <p>The links inside it survive and fall back to sitting directly under their group, which
     * is what the {@code ON DELETE SET NULL} rule on the schema is for.</p>
     *
     * @param subgroupId identifier of the subgroup to delete
     * @param ownerId    identifier of the user that must own it
     * @return {@code true} when a row was deleted, {@code false} when there was nothing to delete
     */
    @Transactional
    public boolean deleteSubgroupFromPushedOperationIfPresent(
            final UUID subgroupId,
            final UUID ownerId) {

        final Optional<SubgroupEntity> existingSubgroup =
                subgroupRepository.findByIdAndOwnerId(subgroupId, ownerId);

        if (existingSubgroup.isEmpty()) {
            return false;
        }
        hierarchyDeletionService.deleteSubgroupWithDescendants(existingSubgroup.get());
        userDataChangePublisher.publishChangeFor(ownerId);
        return true;
    }

    /**
     * Inserts a subgroup, under the client's identifier when that identifier is still free.
     *
     * @param requestedSubgroupId identifier the client asked for
     * @param parentGroup         group the subgroup is nested in
     * @param values              the fields to store
     * @return the inserted entity
     */
    private SubgroupEntity insertSubgroup(
            final UUID requestedSubgroupId,
            final GroupEntity parentGroup,
            final SubgroupSynchronizedValuesDto values) {

        final SubgroupEntity newSubgroup = new SubgroupEntity(
                parentGroup, values.name(), values.position(), values.collapseState());
        newSubgroup.setDescription(values.description());

        newSubgroup.setId(ClientAssignedIdentifierPolicy.chooseIdentifierForInsert(
                requestedSubgroupId, subgroupRepository::existsById));

        return subgroupRepository.save(newSubgroup);
    }

    /**
     * Overwrites every synchronized field of an existing subgroup, including its group.
     *
     * @param existingSubgroup the managed entity to update
     * @param parentGroup      group the subgroup is nested in
     * @param values           the fields to store
     * @return the same entity, updated
     */
    private SubgroupEntity applyValues(
            final SubgroupEntity existingSubgroup,
            final GroupEntity parentGroup,
            final SubgroupSynchronizedValuesDto values) {

        existingSubgroup.setParentGroup(parentGroup);
        existingSubgroup.setName(values.name());
        existingSubgroup.setDescription(values.description());
        existingSubgroup.setCollapsed(values.collapseState().collapsed());
        existingSubgroup.setDefaultCollapsed(values.collapseState().defaultCollapsed());
        existingSubgroup.setPosition(values.position());
        return existingSubgroup;
    }
}
