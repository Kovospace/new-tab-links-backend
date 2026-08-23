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
     * @return the environment's groups, empty when there are none
     */
    @Transactional(readOnly = true)
    public List<GroupDto> findGroupsByEnvironment(final UUID environmentId) {
        return groupMapper.toDtoList(
                groupRepository.findAllByEnvironmentIdOrderByPositionAsc(environmentId));
    }

    /**
     * Returns a single group.
     *
     * @param groupId identifier of the group
     * @return the group
     * @throws ResourceNotFoundException when no group has that identifier
     */
    @Transactional(readOnly = true)
    public GroupDto findGroupById(final UUID groupId) {
        return groupMapper.toDto(getRequiredGroupEntity(groupId));
    }

    /**
     * Creates a group and appends it after the environment's existing ones.
     *
     * @param saveRequest the group to create
     * @return the created group, including its assigned identifier and position
     * @throws ResourceNotFoundException when the owning environment does not exist
     */
    @Transactional
    public GroupDto createGroup(final GroupSaveRequestDto saveRequest) {
        final EnvironmentEntity environment =
                environmentService.getRequiredEnvironmentEntity(saveRequest.environmentId());

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
     * @return the updated group
     * @throws ResourceNotFoundException when no group has that identifier
     */
    @Transactional
    public GroupDto updateGroup(final UUID groupId, final GroupSaveRequestDto saveRequest) {
        final GroupEntity existingGroup = getRequiredGroupEntity(groupId);
        existingGroup.setName(saveRequest.name());
        return groupMapper.toDto(existingGroup);
    }

    /**
     * Deletes a group.
     *
     * @param groupId identifier of the group to delete
     * @throws ResourceNotFoundException when no group has that identifier
     */
    @Transactional
    public void deleteGroup(final UUID groupId) {
        groupRepository.delete(getRequiredGroupEntity(groupId));
    }

    /**
     * Loads a group entity for another service in this application.
     *
     * @param groupId identifier of the group
     * @return the managed entity
     * @throws ResourceNotFoundException when no group has that identifier
     */
    @Transactional(readOnly = true)
    public GroupEntity getRequiredGroupEntity(final UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, groupId));
    }
}
