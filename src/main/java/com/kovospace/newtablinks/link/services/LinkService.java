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
     * @return the group's direct links in display order, empty when there are none
     */
    @Transactional(readOnly = true)
    public List<LinkDto> findDirectLinksOfGroup(final UUID parentGroupId) {
        return linkMapper.toDtoList(
                linkRepository.findAllByParentGroupIdAndParentSubgroupIsNullOrderByPositionAsc(parentGroupId));
    }

    /**
     * Lists the links nested in a subgroup.
     *
     * @param parentSubgroupId identifier of the owning subgroup
     * @return the subgroup's links in display order, empty when there are none
     */
    @Transactional(readOnly = true)
    public List<LinkDto> findLinksOfSubgroup(final UUID parentSubgroupId) {
        return linkMapper.toDtoList(
                linkRepository.findAllByParentSubgroupIdOrderByPositionAsc(parentSubgroupId));
    }

    /**
     * Returns a single link.
     *
     * @param linkId identifier of the link
     * @return the link
     * @throws ResourceNotFoundException when no link has that identifier
     */
    @Transactional(readOnly = true)
    public LinkDto findLinkById(final UUID linkId) {
        return linkMapper.toDto(getRequiredLinkEntity(linkId));
    }

    /**
     * Creates a link and appends it after its siblings.
     *
     * <p>Siblings are the other links of the same subgroup, or the other direct links of the
     * group when the request names no subgroup. A missing favicon address is derived from the
     * link address; see {@link FaviconUrlResolver}.</p>
     *
     * @param saveRequest the link to create
     * @return the created link, including its assigned identifier and position
     * @throws ResourceNotFoundException when the named group or subgroup does not exist
     */
    @Transactional
    public LinkDto createLink(final LinkSaveRequestDto saveRequest) {
        final GroupEntity parentGroup =
                groupService.getRequiredGroupEntity(saveRequest.parentGroupId());
        final SubgroupEntity parentSubgroup = resolveOptionalSubgroup(saveRequest.parentSubgroupId());

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
     * @return the updated link
     * @throws ResourceNotFoundException when the link, or the named subgroup, does not exist
     */
    @Transactional
    public LinkDto updateLink(final UUID linkId, final LinkSaveRequestDto saveRequest) {
        final LinkEntity existingLink = getRequiredLinkEntity(linkId);

        existingLink.setTitle(saveRequest.title());
        existingLink.setUrl(saveRequest.url());
        existingLink.setFaviconUrl(
                FaviconUrlResolver.resolveFaviconUrl(saveRequest.faviconUrl(), saveRequest.url()));
        existingLink.setParentSubgroup(resolveOptionalSubgroup(saveRequest.parentSubgroupId()));

        return linkMapper.toDto(existingLink);
    }

    /**
     * Deletes a link.
     *
     * @param linkId identifier of the link to delete
     * @throws ResourceNotFoundException when no link has that identifier
     */
    @Transactional
    public void deleteLink(final UUID linkId) {
        linkRepository.delete(getRequiredLinkEntity(linkId));
    }

    /**
     * Loads a link entity for another service in this application.
     *
     * @param linkId identifier of the link
     * @return the managed entity
     * @throws ResourceNotFoundException when no link has that identifier
     */
    @Transactional(readOnly = true)
    public LinkEntity getRequiredLinkEntity(final UUID linkId) {
        return linkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, linkId));
    }

    /**
     * Resolves the optional subgroup named by a request.
     *
     * @param parentSubgroupId identifier of the subgroup, or {@code null} when none was named
     * @return the managed subgroup, or {@code null} when none was named
     * @throws ResourceNotFoundException when an identifier was named but matches no subgroup
     */
    private SubgroupEntity resolveOptionalSubgroup(final UUID parentSubgroupId) {
        if (parentSubgroupId == null) {
            return null;
        }
        return subgroupService.getRequiredSubgroupEntity(parentSubgroupId);
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
