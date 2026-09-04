package com.kovospace.newtablinks.group.services;

import com.kovospace.newtablinks.common.utils.ClientAssignedIdentifierPolicy;
import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import com.kovospace.newtablinks.group.dtos.GroupSynchronizedValuesDto;
import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.group.repositories.GroupRepository;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies pushed synchronization operations to groups.
 *
 * <p>Separate from {@link GroupService} for the reason given on
 * {@link com.kovospace.newtablinks.profile.services.ProfileSynchronizationService}, and nothing
 * here throws when a row is absent, for the reason given there too.</p>
 *
 * <p>The owner is read from the parent environment rather than passed in, so a pushed group
 * cannot land under an environment the caller does not own.</p>
 *
 * @since 0.0.6
 */
@Service
public class GroupSynchronizationService {

    private final GroupRepository groupRepository;
    private final UserDataChangePublisher userDataChangePublisher;

    /**
     * Creates the service.
     *
     * @param groupRepository         persistence access for groups
     * @param userDataChangePublisher announces changes to the user's other browsers
     */
    public GroupSynchronizationService(
            final GroupRepository groupRepository,
            final UserDataChangePublisher userDataChangePublisher) {

        this.groupRepository = groupRepository;
        this.userDataChangePublisher = userDataChangePublisher;
    }

    /**
     * Looks a group up without throwing when it is absent or somebody else's.
     *
     * @param groupId identifier of the group
     * @param ownerId identifier of the user that must own it
     * @return the managed entity, or an empty optional when it does not exist or is not theirs
     */
    @Transactional(readOnly = true)
    public Optional<GroupEntity> findGroupEntityForOwner(final UUID groupId, final UUID ownerId) {
        return groupRepository.findByIdAndOwnerId(groupId, ownerId);
    }

    /**
     * Stores a group the client pushed, updating the owner's existing row or inserting a new one.
     *
     * @param requestedGroupId  identifier the client wants the group stored under
     * @param parentEnvironment environment the group is displayed in, already resolved against
     *                          the caller's own identifier
     * @param values            the fields to store, all of which are replaced
     * @return the stored entity, whose identifier differs from the requested one exactly when the
     *         requested one was already taken
     */
    @Transactional
    public GroupEntity upsertGroupFromPushedOperation(
            final UUID requestedGroupId,
            final EnvironmentEntity parentEnvironment,
            final GroupSynchronizedValuesDto values) {

        final UUID ownerId = parentEnvironment.getOwner().getId();
        final Optional<GroupEntity> existingGroup =
                groupRepository.findByIdAndOwnerId(requestedGroupId, ownerId);

        userDataChangePublisher.publishChangeFor(ownerId);

        if (existingGroup.isPresent()) {
            return applyValues(existingGroup.get(), parentEnvironment, values);
        }
        return insertGroup(requestedGroupId, parentEnvironment, values);
    }

    /**
     * Deletes a group if the owner still has one under that identifier.
     *
     * @param groupId identifier of the group to delete
     * @param ownerId identifier of the user that must own it
     * @return {@code true} when a row was deleted, {@code false} when there was nothing to delete
     */
    @Transactional
    public boolean deleteGroupFromPushedOperationIfPresent(
            final UUID groupId,
            final UUID ownerId) {

        final Optional<GroupEntity> existingGroup =
                groupRepository.findByIdAndOwnerId(groupId, ownerId);

        if (existingGroup.isEmpty()) {
            return false;
        }
        groupRepository.delete(existingGroup.get());
        userDataChangePublisher.publishChangeFor(ownerId);
        return true;
    }

    /**
     * Inserts a group, under the client's identifier when that identifier is still free.
     *
     * @param requestedGroupId  identifier the client asked for
     * @param parentEnvironment environment the group is displayed in
     * @param values            the fields to store
     * @return the inserted entity
     */
    private GroupEntity insertGroup(
            final UUID requestedGroupId,
            final EnvironmentEntity parentEnvironment,
            final GroupSynchronizedValuesDto values) {

        final GroupEntity newGroup = new GroupEntity(
                parentEnvironment, values.name(), values.description(), values.position());

        newGroup.setId(ClientAssignedIdentifierPolicy.chooseIdentifierForInsert(
                requestedGroupId, groupRepository::existsById));

        return groupRepository.save(newGroup);
    }

    /**
     * Overwrites every synchronized field of an existing group, including its environment.
     *
     * @param existingGroup     the managed entity to update
     * @param parentEnvironment environment the group is displayed in
     * @param values            the fields to store
     * @return the same entity, updated
     */
    private GroupEntity applyValues(
            final GroupEntity existingGroup,
            final EnvironmentEntity parentEnvironment,
            final GroupSynchronizedValuesDto values) {

        existingGroup.setEnvironment(parentEnvironment);
        existingGroup.setName(values.name());
        existingGroup.setDescription(values.description());
        existingGroup.setPosition(values.position());
        return existingGroup;
    }
}
