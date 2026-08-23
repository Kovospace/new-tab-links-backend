package com.kovospace.newtablinks.subgroup.services;

import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.utils.DisplayPositionCalculator;
import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.group.services.GroupService;
import com.kovospace.newtablinks.subgroup.dtos.SubgroupDto;
import com.kovospace.newtablinks.subgroup.dtos.SubgroupSaveRequestDto;
import com.kovospace.newtablinks.subgroup.mappers.SubgroupMapper;
import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
import com.kovospace.newtablinks.subgroup.repositories.SubgroupRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business operations on subgroups.
 *
 * @since 0.0.1
 */
@Service
public class SubgroupService {

    private static final String RESOURCE_NAME = "Subgroup";

    private final SubgroupRepository subgroupRepository;
    private final SubgroupMapper subgroupMapper;
    private final GroupService groupService;

    /**
     * Creates the service.
     *
     * @param subgroupRepository persistence access for subgroups
     * @param subgroupMapper     converter to the client facing shape
     * @param groupService       resolves the owning group
     */
    public SubgroupService(
            final SubgroupRepository subgroupRepository,
            final SubgroupMapper subgroupMapper,
            final GroupService groupService) {

        this.subgroupRepository = subgroupRepository;
        this.subgroupMapper = subgroupMapper;
        this.groupService = groupService;
    }

    /**
     * Lists a group's subgroups in display order.
     *
     * @param parentGroupId identifier of the owning group
     * @return the group's subgroups, empty when there are none
     */
    @Transactional(readOnly = true)
    public List<SubgroupDto> findSubgroupsByParentGroup(final UUID parentGroupId) {
        return subgroupMapper.toDtoList(
                subgroupRepository.findAllByParentGroupIdOrderByPositionAsc(parentGroupId));
    }

    /**
     * Returns a single subgroup.
     *
     * @param subgroupId identifier of the subgroup
     * @return the subgroup
     * @throws ResourceNotFoundException when no subgroup has that identifier
     */
    @Transactional(readOnly = true)
    public SubgroupDto findSubgroupById(final UUID subgroupId) {
        return subgroupMapper.toDto(getRequiredSubgroupEntity(subgroupId));
    }

    /**
     * Creates a subgroup and appends it after the group's existing ones.
     *
     * @param saveRequest the subgroup to create
     * @return the created subgroup, including its assigned identifier and position
     * @throws ResourceNotFoundException when the owning group does not exist
     */
    @Transactional
    public SubgroupDto createSubgroup(final SubgroupSaveRequestDto saveRequest) {
        final GroupEntity parentGroup =
                groupService.getRequiredGroupEntity(saveRequest.parentGroupId());

        final int position = DisplayPositionCalculator.calculatePositionForAppendedItem(
                subgroupRepository.findHighestPositionByGroupId(parentGroup.getId()));

        final SubgroupEntity newSubgroup = new SubgroupEntity(
                parentGroup, saveRequest.name(), position, saveRequest.collapsed());

        return subgroupMapper.toDto(subgroupRepository.save(newSubgroup));
    }

    /**
     * Updates the name and folded state of an existing subgroup.
     *
     * @param subgroupId  identifier of the subgroup to update
     * @param saveRequest the values to store
     * @return the updated subgroup
     * @throws ResourceNotFoundException when no subgroup has that identifier
     */
    @Transactional
    public SubgroupDto updateSubgroup(
            final UUID subgroupId,
            final SubgroupSaveRequestDto saveRequest) {

        final SubgroupEntity existingSubgroup = getRequiredSubgroupEntity(subgroupId);
        existingSubgroup.setName(saveRequest.name());
        existingSubgroup.setCollapsed(saveRequest.collapsed());
        return subgroupMapper.toDto(existingSubgroup);
    }

    /**
     * Deletes a subgroup.
     *
     * @param subgroupId identifier of the subgroup to delete
     * @throws ResourceNotFoundException when no subgroup has that identifier
     */
    @Transactional
    public void deleteSubgroup(final UUID subgroupId) {
        subgroupRepository.delete(getRequiredSubgroupEntity(subgroupId));
    }

    /**
     * Loads a subgroup entity for another service in this application.
     *
     * @param subgroupId identifier of the subgroup
     * @return the managed entity
     * @throws ResourceNotFoundException when no subgroup has that identifier
     */
    @Transactional(readOnly = true)
    public SubgroupEntity getRequiredSubgroupEntity(final UUID subgroupId) {
        return subgroupRepository.findById(subgroupId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, subgroupId));
    }
}
