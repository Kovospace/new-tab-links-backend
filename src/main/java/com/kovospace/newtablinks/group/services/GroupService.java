package com.kovospace.newtablinks.group.services;

import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.utils.DisplayPositionCalculator;
import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import com.kovospace.newtablinks.environment.services.EnvironmentService;
import com.kovospace.newtablinks.group.dtos.GroupDto;
import com.kovospace.newtablinks.group.dtos.GroupSaveRequestDto;
import com.kovospace.newtablinks.group.mappers.GroupMapper;
import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.group.repositories.GroupRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business operations on groups.
 *
 * <p>Every method takes the identifier of the user the request is authenticated as, and every
 * lookup is scoped to it. A row belonging to somebody else is reported as missing rather than as
 * forbidden, so the API cannot be used to confirm that another user's identifier exists.</p>
 *
 * @since 0.0.1
 */
@Service
public class GroupService {

    private static final String RESOURCE_NAME = "Group";

    private final GroupRepository groupRepository;
    private final GroupMapper groupMapper;
    private final EnvironmentService environmentService;

    /**
     * Creates the service.
     *
     * @param groupRepository    persistence access for groups
     * @param groupMapper        converter to the client facing shape
     * @param environmentService resolves the owning environment
     */
    public GroupService(
            final GroupRepository groupRepository,
            final GroupMapper groupMapper,
            final EnvironmentService environmentService) {

        this.groupRepository = groupRepository;
        this.groupMapper = groupMapper;
        this.environmentService = environmentService;
    }

    /**
     * Lists an environment's groups in display order.
     *
     * @param environmentId identifier of the owning environment
     * @param ownerId       identifier of the user that must own it
     * @return the groups, empty when there are none or the environment is not theirs
     */
    @Transactional(readOnly = true)
    public List<GroupDto> findGroupsByEnvironment(final UUID environmentId, final UUID ownerId) {
        return groupMapper.toDtoList(
                groupRepository.findAllByEnvironmentIdAndOwnerIdOrderByPositionAsc(
                        environmentId, ownerId));
    }

    /**
     * Returns a single group.
     *
     * @param groupId identifier of the group
     * @param ownerId identifier of the user that must own it
     * @return the group
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional(readOnly = true)
    public GroupDto findGroupById(final UUID groupId, final UUID ownerId) {
        return groupMapper.toDto(getRequiredGroupEntity(groupId, ownerId));
    }

    /**
     * Creates a group and appends it after the environment's existing ones.
     *
     * @param saveRequest the group to create
     * @param ownerId     identifier of the user that must own the target environment
     * @return the created group, including its assigned identifier and position
     * @throws ResourceNotFoundException when the environment does not exist or is not theirs
     */
    @Transactional
    public GroupDto createGroup(final GroupSaveRequestDto saveRequest, final UUID ownerId) {
        final EnvironmentEntity environment = environmentService.getRequiredEnvironmentEntity(
                saveRequest.environmentId(), ownerId);

        final int position = DisplayPositionCalculator.calculatePositionForAppendedItem(
                groupRepository.findHighestPositionByEnvironmentId(environment.getId()));

        final GroupEntity newGroup = new GroupEntity(environment, saveRequest.name(), position);
        return groupMapper.toDto(groupRepository.save(newGroup));
    }

    /**
     * Renames an existing group.
     *
     * @param groupId     identifier of the group to update
     * @param saveRequest the values to store
     * @param ownerId     identifier of the user that must own it
     * @return the updated group
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional
    public GroupDto updateGroup(
            final UUID groupId,
            final GroupSaveRequestDto saveRequest,
            final UUID ownerId) {

        final GroupEntity existingGroup = getRequiredGroupEntity(groupId, ownerId);
        existingGroup.setName(saveRequest.name());
        return groupMapper.toDto(existingGroup);
    }

    /**
     * Deletes a group.
     *
     * @param groupId identifier of the group to delete
     * @param ownerId identifier of the user that must own it
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional
    public void deleteGroup(final UUID groupId, final UUID ownerId) {
        groupRepository.delete(getRequiredGroupEntity(groupId, ownerId));
    }

    /**
     * Loads a group entity for another service in this application, enforcing ownership.
     *
     * @param groupId identifier of the group
     * @param ownerId identifier of the user that must own it
     * @return the managed entity
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional(readOnly = true)
    public GroupEntity getRequiredGroupEntity(final UUID groupId, final UUID ownerId) {
        return groupRepository.findByIdAndOwnerId(groupId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, groupId));
    }
}
