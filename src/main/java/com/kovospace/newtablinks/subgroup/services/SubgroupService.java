package com.kovospace.newtablinks.subgroup.services;

import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.services.HierarchyDeletionService;
import com.kovospace.newtablinks.common.utils.DisplayPositionCalculator;
import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.group.services.GroupService;
import com.kovospace.newtablinks.subgroup.dtos.SubgroupDto;
import com.kovospace.newtablinks.subgroup.dtos.SubgroupSaveRequestDto;
import com.kovospace.newtablinks.subgroup.mappers.SubgroupMapper;
import com.kovospace.newtablinks.subgroup.models.SubgroupCollapseState;
import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
import com.kovospace.newtablinks.subgroup.repositories.SubgroupRepository;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business operations on subgroups.
 *
 * <p>Ownership is enforced on every lookup; see {@link GroupService} for the reasoning.</p>
 *
 * @since 0.0.1
 */
@Service
public class SubgroupService {

    private static final String RESOURCE_NAME = "Subgroup";

    private final SubgroupRepository subgroupRepository;
    private final SubgroupMapper subgroupMapper;
    private final GroupService groupService;
    private final UserDataChangePublisher userDataChangePublisher;
    private final HierarchyDeletionService hierarchyDeletionService;

    /**
     * Creates the service.
     *
     * @param subgroupRepository       persistence access for subgroups
     * @param subgroupMapper           converter to the client facing shape
     * @param groupService             resolves the owning group
     * @param userDataChangePublisher  announces changes to the user's other browsers
     * @param hierarchyDeletionService removes a record together with everything beneath it
     */
    public SubgroupService(
            final SubgroupRepository subgroupRepository,
            final SubgroupMapper subgroupMapper,
            final GroupService groupService,
            final UserDataChangePublisher userDataChangePublisher,
            final HierarchyDeletionService hierarchyDeletionService) {

        this.subgroupRepository = subgroupRepository;
        this.subgroupMapper = subgroupMapper;
        this.groupService = groupService;
        this.userDataChangePublisher = userDataChangePublisher;
        this.hierarchyDeletionService = hierarchyDeletionService;
    }

    /**
     * Lists a group's subgroups in display order.
     *
     * @param parentGroupId identifier of the owning group
     * @param ownerId       identifier of the user that must own it
     * @return the subgroups, empty when there are none or the group is not theirs
     */
    @Transactional(readOnly = true)
    public List<SubgroupDto> findSubgroupsByParentGroup(
            final UUID parentGroupId,
            final UUID ownerId) {

        return subgroupMapper.toDtoList(
                subgroupRepository.findAllByParentGroupIdAndOwnerIdOrderByPositionAsc(
                        parentGroupId, ownerId));
    }

    /**
     * Returns a single subgroup.
     *
     * @param subgroupId identifier of the subgroup
     * @param ownerId    identifier of the user that must own it
     * @return the subgroup
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional(readOnly = true)
    public SubgroupDto findSubgroupById(final UUID subgroupId, final UUID ownerId) {
        return subgroupMapper.toDto(getRequiredSubgroupEntity(subgroupId, ownerId));
    }

    /**
     * Creates a subgroup and appends it after the group's existing ones.
     *
     * @param saveRequest the subgroup to create
     * @param ownerId     identifier of the user that must own the target group
     * @return the created subgroup, including its assigned identifier and position
     * @throws ResourceNotFoundException when the group does not exist or is not theirs
     */
    @Transactional
    public SubgroupDto createSubgroup(
            final SubgroupSaveRequestDto saveRequest,
            final UUID ownerId) {

        final GroupEntity parentGroup =
                groupService.getRequiredGroupEntity(saveRequest.parentGroupId(), ownerId);

        final int position = DisplayPositionCalculator.calculatePositionForAppendedItem(
                subgroupRepository.findHighestPositionByGroupId(parentGroup.getId()));

        final SubgroupEntity newSubgroup = new SubgroupEntity(
                parentGroup,
                saveRequest.name(),
                position,
                new SubgroupCollapseState(saveRequest.collapsed(), saveRequest.defaultCollapsed()));

        newSubgroup.setDescription(saveRequest.description());
        newSubgroup.setCatchLinksIntoTabGroup(saveRequest.catchLinksIntoTabGroup());
        newSubgroup.setColor(saveRequest.color());

        userDataChangePublisher.publishChangeFor(ownerId);
        return subgroupMapper.toDto(subgroupRepository.save(newSubgroup));
    }

    /**
     * Updates the name, description, both folded states, the tab group setting and the colour
     * of a subgroup.
     *
     * @param subgroupId  identifier of the subgroup to update
     * @param saveRequest the values to store
     * @param ownerId     identifier of the user that must own it
     * @return the updated subgroup
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional
    public SubgroupDto updateSubgroup(
            final UUID subgroupId,
            final SubgroupSaveRequestDto saveRequest,
            final UUID ownerId) {

        final SubgroupEntity existingSubgroup = getRequiredSubgroupEntity(subgroupId, ownerId);
        existingSubgroup.setName(saveRequest.name());
        existingSubgroup.setDescription(saveRequest.description());
        existingSubgroup.setCollapsed(saveRequest.collapsed());
        existingSubgroup.setDefaultCollapsed(saveRequest.defaultCollapsed());
        existingSubgroup.setCatchLinksIntoTabGroup(saveRequest.catchLinksIntoTabGroup());
        existingSubgroup.setColor(saveRequest.color());
        userDataChangePublisher.publishChangeFor(ownerId);
        return subgroupMapper.toDto(existingSubgroup);
    }

    /**
     * Deletes a subgroup and the links nested in it.
     *
     * @param subgroupId identifier of the subgroup to delete
     * @param ownerId    identifier of the user that must own it
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional
    public void deleteSubgroup(final UUID subgroupId, final UUID ownerId) {
        hierarchyDeletionService.deleteSubgroupWithDescendants(
                getRequiredSubgroupEntity(subgroupId, ownerId));
        userDataChangePublisher.publishChangeFor(ownerId);
    }

    /**
     * Loads a subgroup entity for another service in this application, enforcing ownership.
     *
     * @param subgroupId identifier of the subgroup
     * @param ownerId    identifier of the user that must own it
     * @return the managed entity
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional(readOnly = true)
    public SubgroupEntity getRequiredSubgroupEntity(final UUID subgroupId, final UUID ownerId) {
        return subgroupRepository.findByIdAndOwnerId(subgroupId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, subgroupId));
    }
}
