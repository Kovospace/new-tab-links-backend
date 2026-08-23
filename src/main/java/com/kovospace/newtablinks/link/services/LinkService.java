package com.kovospace.newtablinks.link.services;

import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.utils.DisplayPositionCalculator;
import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.group.services.GroupService;
import com.kovospace.newtablinks.link.dtos.LinkDto;
import com.kovospace.newtablinks.link.dtos.LinkSaveRequestDto;
import com.kovospace.newtablinks.link.mappers.LinkMapper;
import com.kovospace.newtablinks.link.models.LinkEntity;
import com.kovospace.newtablinks.link.repositories.LinkRepository;
import com.kovospace.newtablinks.link.utils.FaviconUrlResolver;
import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
import com.kovospace.newtablinks.subgroup.services.SubgroupService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business operations on links.
 *
 * <p>Ownership is enforced on every lookup; see
 * {@link com.kovospace.newtablinks.group.services.GroupService} for the reasoning.</p>
 *
 * @since 0.0.1
 */
@Service
public class LinkService {

    private static final String RESOURCE_NAME = "Link";

    private final LinkRepository linkRepository;
    private final LinkMapper linkMapper;
    private final GroupService groupService;
    private final SubgroupService subgroupService;

    /**
     * Creates the service.
     *
     * @param linkRepository  persistence access for links
     * @param linkMapper      converter to the client facing shape
     * @param groupService    resolves the owning group
     * @param subgroupService resolves the optional owning subgroup
     */
    public LinkService(
            final LinkRepository linkRepository,
            final LinkMapper linkMapper,
            final GroupService groupService,
            final SubgroupService subgroupService) {

        this.linkRepository = linkRepository;
        this.linkMapper = linkMapper;
        this.groupService = groupService;
        this.subgroupService = subgroupService;
    }

    /**
     * Lists the links sitting directly in a group, excluding those nested in its subgroups.
     *
     * @param parentGroupId identifier of the owning group
     * @param ownerId       identifier of the user that must own it
     * @return the links, empty when there are none or the group is not theirs
     */
    @Transactional(readOnly = true)
    public List<LinkDto> findDirectLinksOfGroup(final UUID parentGroupId, final UUID ownerId) {
        return linkMapper.toDtoList(
                linkRepository.findDirectGroupLinksForOwner(parentGroupId, ownerId));
    }

    /**
     * Lists the links nested in a subgroup.
     *
     * @param parentSubgroupId identifier of the owning subgroup
     * @param ownerId          identifier of the user that must own it
     * @return the links, empty when there are none or the subgroup is not theirs
     */
    @Transactional(readOnly = true)
    public List<LinkDto> findLinksOfSubgroup(final UUID parentSubgroupId, final UUID ownerId) {
        return linkMapper.toDtoList(
                linkRepository.findSubgroupLinksForOwner(parentSubgroupId, ownerId));
    }

    /**
     * Returns a single link.
     *
     * @param linkId  identifier of the link
     * @param ownerId identifier of the user that must own it
     * @return the link
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional(readOnly = true)
    public LinkDto findLinkById(final UUID linkId, final UUID ownerId) {
        return linkMapper.toDto(getRequiredLinkEntity(linkId, ownerId));
    }

    /**
     * Creates a link and appends it after its siblings.
     *
     * <p>Siblings are the other links of the same subgroup, or the other direct links of the
     * group when the request names no subgroup. A missing favicon address is derived from the
     * link address; see {@link FaviconUrlResolver}.</p>
     *
     * @param saveRequest the link to create
     * @param ownerId     identifier of the user that must own the target group and subgroup
     * @return the created link, including its assigned identifier and position
     * @throws ResourceNotFoundException when the group or subgroup does not exist or is not theirs
     */
    @Transactional
    public LinkDto createLink(final LinkSaveRequestDto saveRequest, final UUID ownerId) {
        final GroupEntity parentGroup =
                groupService.getRequiredGroupEntity(saveRequest.parentGroupId(), ownerId);
        final SubgroupEntity parentSubgroup =
                resolveOptionalSubgroup(saveRequest.parentSubgroupId(), ownerId);

        final LinkEntity newLink = new LinkEntity(
                parentGroup,
                parentSubgroup,
                saveRequest.title(),
                saveRequest.url(),
                FaviconUrlResolver.resolveFaviconUrl(saveRequest.faviconUrl(), saveRequest.url()),
                calculatePositionAmongSiblings(parentGroup.getId(), parentSubgroup));

        return linkMapper.toDto(linkRepository.save(newLink));
    }

    /**
     * Replaces the changeable fields of an existing link, including which subgroup it sits in.
     *
     * <p>The position is left untouched: reordering is a separate concern from editing, and a
     * dedicated move operation will own it.</p>
     *
     * @param linkId      identifier of the link to update
     * @param saveRequest the values to store
     * @param ownerId     identifier of the user that must own it
     * @return the updated link
     * @throws ResourceNotFoundException when the link or subgroup does not exist or is not theirs
     */
    @Transactional
    public LinkDto updateLink(
            final UUID linkId,
            final LinkSaveRequestDto saveRequest,
            final UUID ownerId) {

        final LinkEntity existingLink = getRequiredLinkEntity(linkId, ownerId);

        existingLink.setTitle(saveRequest.title());
        existingLink.setUrl(saveRequest.url());
        existingLink.setFaviconUrl(
                FaviconUrlResolver.resolveFaviconUrl(saveRequest.faviconUrl(), saveRequest.url()));
        existingLink.setParentSubgroup(
                resolveOptionalSubgroup(saveRequest.parentSubgroupId(), ownerId));

        return linkMapper.toDto(existingLink);
    }

    /**
     * Deletes a link.
     *
     * @param linkId  identifier of the link to delete
     * @param ownerId identifier of the user that must own it
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional
    public void deleteLink(final UUID linkId, final UUID ownerId) {
        linkRepository.delete(getRequiredLinkEntity(linkId, ownerId));
    }

    /**
     * Loads a link entity for another service in this application, enforcing ownership.
     *
     * @param linkId  identifier of the link
     * @param ownerId identifier of the user that must own it
     * @return the managed entity
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional(readOnly = true)
    public LinkEntity getRequiredLinkEntity(final UUID linkId, final UUID ownerId) {
        return linkRepository.findByIdAndOwnerId(linkId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, linkId));
    }

    /**
     * Resolves the optional subgroup named by a request.
     *
     * @param parentSubgroupId identifier of the subgroup, or {@code null} when none was named
     * @param ownerId          identifier of the user that must own it
     * @return the managed subgroup, or {@code null} when none was named
     * @throws ResourceNotFoundException when named but missing or owned by somebody else
     */
    private SubgroupEntity resolveOptionalSubgroup(
            final UUID parentSubgroupId,
            final UUID ownerId) {

        if (parentSubgroupId == null) {
            return null;
        }
        return subgroupService.getRequiredSubgroupEntity(parentSubgroupId, ownerId);
    }

    /**
     * Works out the position for a link appended after its siblings.
     *
     * @param parentGroupId  identifier of the owning group
     * @param parentSubgroup owning subgroup, or {@code null} for a direct child of the group
     * @return the position to assign
     */
    private int calculatePositionAmongSiblings(
            final UUID parentGroupId,
            final SubgroupEntity parentSubgroup) {

        final Integer highestPositionInUse = parentSubgroup == null
                ? linkRepository.findHighestPositionAmongDirectGroupLinks(parentGroupId)
                : linkRepository.findHighestPositionAmongSubgroupLinks(parentSubgroup.getId());

        return DisplayPositionCalculator.calculatePositionForAppendedItem(highestPositionInUse);
    }
}
