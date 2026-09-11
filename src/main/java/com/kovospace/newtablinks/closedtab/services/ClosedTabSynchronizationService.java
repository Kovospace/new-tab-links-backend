package com.kovospace.newtablinks.closedtab.services;

import com.kovospace.newtablinks.closedtab.dtos.ClosedTabSynchronizedValuesDto;
import com.kovospace.newtablinks.closedtab.models.ClosedTabEntity;
import com.kovospace.newtablinks.closedtab.repositories.ClosedTabRepository;
import com.kovospace.newtablinks.common.utils.ClientAssignedIdentifierPolicy;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies pushed synchronization operations to closed tabs.
 *
 * <p>The only module with no interactive service beside this one. Nothing but a browser closing
 * a tab creates one of these records, so there is no endpoint to create, edit or reorder them and
 * no controller to expose one - an empty layer is not added for symmetry.</p>
 *
 * <p>Nothing here throws when a row is absent, for the reason given on
 * {@link com.kovospace.newtablinks.profile.services.ProfileSynchronizationService}: an exception
 * crossing a {@code @Transactional} boundary would take the rest of the pushed batch with it.
 * That matters more here than anywhere else, because deletes of closed tabs are routine - the
 * extension caps the list and pushes what it prunes - and a delete replayed twice is normal
 * traffic rather than a fault.</p>
 *
 * @since 0.0.8
 */
@Service
public class ClosedTabSynchronizationService {

    private final ClosedTabRepository closedTabRepository;
    private final UserDataChangePublisher userDataChangePublisher;

    /**
     * Creates the service.
     *
     * @param closedTabRepository     persistence access for closed tabs
     * @param userDataChangePublisher announces changes to the user's other browsers
     */
    public ClosedTabSynchronizationService(
            final ClosedTabRepository closedTabRepository,
            final UserDataChangePublisher userDataChangePublisher) {

        this.closedTabRepository = closedTabRepository;
        this.userDataChangePublisher = userDataChangePublisher;
    }

    /**
     * Stores a closed tab the client pushed, updating the owner's existing row or inserting a new
     * one.
     *
     * <p>Every synchronized field is replaced, including {@code closedAt}, which is stored as the
     * client sent it. No server clock is substituted anywhere on this path: the audit timestamps
     * record when the row reached this server, and they are a different question from when the
     * tab was closed.</p>
     *
     * @param requestedClosedTabId identifier the client wants the closed tab stored under
     * @param profile              profile whose list the tab is on, already resolved against the
     *                             caller's own identifier
     * @param values               the fields to store, all of which are replaced
     * @return the stored entity, whose identifier differs from the requested one exactly when the
     *         requested one was already taken
     */
    @Transactional
    public ClosedTabEntity upsertClosedTabFromPushedOperation(
            final UUID requestedClosedTabId,
            final ProfileEntity profile,
            final ClosedTabSynchronizedValuesDto values) {

        final UUID ownerId = profile.getOwner().getId();
        final Optional<ClosedTabEntity> existingClosedTab =
                closedTabRepository.findByIdAndOwnerId(requestedClosedTabId, ownerId);

        userDataChangePublisher.publishChangeFor(ownerId);

        if (existingClosedTab.isPresent()) {
            return applyValues(existingClosedTab.get(), profile, values);
        }
        return insertClosedTab(requestedClosedTabId, profile, values);
    }

    /**
     * Deletes a closed tab if the owner still has one under that identifier.
     *
     * <p>The common case on this path rather than an exceptional one: the extension keeps a
     * capped list and pushes everything it prunes as a delete.</p>
     *
     * @param closedTabId identifier of the closed tab to delete
     * @param ownerId     identifier of the user that must own it
     * @return {@code true} when a row was deleted, {@code false} when there was nothing to delete
     */
    @Transactional
    public boolean deleteClosedTabFromPushedOperationIfPresent(
            final UUID closedTabId,
            final UUID ownerId) {

        final Optional<ClosedTabEntity> existingClosedTab =
                closedTabRepository.findByIdAndOwnerId(closedTabId, ownerId);

        if (existingClosedTab.isEmpty()) {
            return false;
        }
        closedTabRepository.delete(existingClosedTab.get());
        userDataChangePublisher.publishChangeFor(ownerId);
        return true;
    }

    /**
     * Inserts a closed tab, under the client's identifier when that identifier is still free.
     *
     * @param requestedClosedTabId identifier the client asked for
     * @param profile              profile whose list the tab is on
     * @param values               the fields to store
     * @return the inserted entity
     */
    private ClosedTabEntity insertClosedTab(
            final UUID requestedClosedTabId,
            final ProfileEntity profile,
            final ClosedTabSynchronizedValuesDto values) {

        final ClosedTabEntity newClosedTab = new ClosedTabEntity(
                profile,
                values.url(),
                values.title(),
                values.faviconUrl(),
                values.closedAt(),
                values.deviceName());

        newClosedTab.setId(ClientAssignedIdentifierPolicy.chooseIdentifierForInsert(
                requestedClosedTabId, closedTabRepository::existsById));

        return closedTabRepository.save(newClosedTab);
    }

    /**
     * Overwrites every synchronized field of an existing closed tab.
     *
     * @param existingClosedTab the managed entity to update
     * @param profile           profile whose list the tab is on
     * @param values            the fields to store
     * @return the same entity, updated
     */
    private ClosedTabEntity applyValues(
            final ClosedTabEntity existingClosedTab,
            final ProfileEntity profile,
            final ClosedTabSynchronizedValuesDto values) {

        existingClosedTab.setProfile(profile);
        existingClosedTab.setUrl(values.url());
        existingClosedTab.setTitle(values.title());
        existingClosedTab.setFaviconUrl(values.faviconUrl());
        existingClosedTab.setClosedAt(values.closedAt());
        existingClosedTab.setDeviceName(values.deviceName());
        return existingClosedTab;
    }
}
