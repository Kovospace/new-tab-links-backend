package com.kovospace.newtablinks.link.services;

import com.kovospace.newtablinks.common.utils.ClientAssignedIdentifierPolicy;
import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.link.dtos.LinkSynchronizedValuesDto;
import com.kovospace.newtablinks.link.models.LinkEntity;
import com.kovospace.newtablinks.link.repositories.LinkRepository;
import com.kovospace.newtablinks.link.utils.FaviconUrlResolver;
import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies pushed synchronization operations to links.
 *
 * <p>Separate from {@link LinkService} for the reason given on
 * {@link com.kovospace.newtablinks.profile.services.ProfileSynchronizationService}, and nothing
 * here throws when a row is absent, for the reason given there too.</p>
 *
 * <p>This is where {@link LinkService#updateLink} finally gets its counterpart. That method
 * deliberately leaves the position alone, because editing a link and moving it are different
 * acts and the interactive API has no move operation. A push <em>is</em> the move operation: it
 * replays what the user did on another device, and a drag that reordered a group arrives as
 * exactly these upserts.</p>
 *
 * @since 0.0.6
 */
@Service
public class LinkSynchronizationService {

    private final LinkRepository linkRepository;
    private final UserDataChangePublisher userDataChangePublisher;

    /**
     * Creates the service.
     *
     * @param linkRepository          persistence access for links
     * @param userDataChangePublisher announces changes to the user's other browsers
     */
    public LinkSynchronizationService(
            final LinkRepository linkRepository,
            final UserDataChangePublisher userDataChangePublisher) {

        this.linkRepository = linkRepository;
        this.userDataChangePublisher = userDataChangePublisher;
    }

    /**
     * Looks a link up without throwing when it is absent or somebody else's.
     *
     * @param linkId  identifier of the link
     * @param ownerId identifier of the user that must own it
     * @return the managed entity, or an empty optional when it does not exist or is not theirs
     * @since 0.0.18
     */
    @Transactional(readOnly = true)
    public Optional<LinkEntity> findLinkEntityForOwner(final UUID linkId, final UUID ownerId) {
        return linkRepository.findByIdAndOwnerId(linkId, ownerId);
    }

    /**
     * Stores a link the client pushed, updating the owner's existing row or inserting a new one.
     *
     * @param requestedLinkId identifier the client wants the link stored under
     * @param parentGroup     group the link belongs to, already resolved against the caller's own
     *                        identifier
     * @param parentSubgroup  subgroup the link is nested in, or {@code null} for a link sitting
     *                        directly in the group
     * @param values          the fields to store, all of which are replaced
     * @return the stored entity, whose identifier differs from the requested one exactly when the
     *         requested one was already taken
     */
    @Transactional
    public LinkEntity upsertLinkFromPushedOperation(
            final UUID requestedLinkId,
            final GroupEntity parentGroup,
            final SubgroupEntity parentSubgroup,
            final LinkSynchronizedValuesDto values) {

        final UUID ownerId = parentGroup.getEnvironment().getOwner().getId();
        final Optional<LinkEntity> existingLink =
                linkRepository.findByIdAndOwnerId(requestedLinkId, ownerId);

        userDataChangePublisher.publishChangeFor(ownerId);

        if (existingLink.isPresent()) {
            return applyValues(existingLink.get(), parentGroup, parentSubgroup, values);
        }
        return insertLink(requestedLinkId, parentGroup, parentSubgroup, values);
    }

    /**
     * Deletes a link if the owner still has one under that identifier.
     *
     * @param linkId  identifier of the link to delete
     * @param ownerId identifier of the user that must own it
     * @return {@code true} when a row was deleted, {@code false} when there was nothing to delete
     */
    @Transactional
    public boolean deleteLinkFromPushedOperationIfPresent(
            final UUID linkId,
            final UUID ownerId) {

        final Optional<LinkEntity> existingLink = linkRepository.findByIdAndOwnerId(linkId, ownerId);

        if (existingLink.isEmpty()) {
            return false;
        }
        linkRepository.delete(existingLink.get());
        userDataChangePublisher.publishChangeFor(ownerId);
        return true;
    }

    /**
     * Inserts a link, under the client's identifier when that identifier is still free.
     *
     * @param requestedLinkId identifier the client asked for
     * @param parentGroup     group the link belongs to
     * @param parentSubgroup  subgroup the link is nested in, or {@code null}
     * @param values          the fields to store
     * @return the inserted entity
     */
    private LinkEntity insertLink(
            final UUID requestedLinkId,
            final GroupEntity parentGroup,
            final SubgroupEntity parentSubgroup,
            final LinkSynchronizedValuesDto values) {

        final LinkEntity newLink = new LinkEntity(
                parentGroup,
                parentSubgroup,
                values.title(),
                values.url(),
                FaviconUrlResolver.resolveFaviconUrl(values.faviconUrl(), values.url()),
                values.position());

        newLink.setId(ClientAssignedIdentifierPolicy.chooseIdentifierForInsert(
                requestedLinkId, linkRepository::existsById));

        return linkRepository.save(newLink);
    }

    /**
     * Overwrites every synchronized field of an existing link, including both of its parents.
     *
     * <p>Both parents are replaced, not only the subgroup: a link dragged from one group to
     * another arrives as one upsert naming its new home, and leaving the group behind would put
     * the link in two places at once.</p>
     *
     * @param existingLink   the managed entity to update
     * @param parentGroup    group the link belongs to
     * @param parentSubgroup subgroup the link is nested in, or {@code null} to detach it
     * @param values         the fields to store
     * @return the same entity, updated
     */
    private LinkEntity applyValues(
            final LinkEntity existingLink,
            final GroupEntity parentGroup,
            final SubgroupEntity parentSubgroup,
            final LinkSynchronizedValuesDto values) {

        existingLink.setParentGroup(parentGroup);
        existingLink.setParentSubgroup(parentSubgroup);
        existingLink.setTitle(values.title());
        existingLink.setUrl(values.url());
        existingLink.setFaviconUrl(
                FaviconUrlResolver.resolveFaviconUrl(values.faviconUrl(), values.url()));
        existingLink.setPosition(values.position());
        return existingLink;
    }
}
