package com.kovospace.newtablinks.visit.services;

import com.kovospace.newtablinks.common.utils.ClientAssignedIdentifierPolicy;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import com.kovospace.newtablinks.visit.dtos.VisitSynchronizedValuesDto;
import com.kovospace.newtablinks.visit.models.VisitEntity;
import com.kovospace.newtablinks.visit.repositories.VisitRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies pushed synchronization operations to visits - the browsing history.
 *
 * <p>The twin of {@link com.kovospace.newtablinks.closedtab.services.ClosedTabSynchronizationService}
 * and deliberately built the same way: no interactive service and no controller beside it,
 * because only a browser records a visit; nothing here throws when a row is absent, because a
 * delete replayed twice is ordinary traffic for a rolling list; and the history never refuses a
 * push, its oldest entries roll off instead.</p>
 *
 * <p>Nothing here looks at the profile's {@code recordBrowsingHistory} setting. Whether to record
 * is the extension's decision, and so is clearing the history when recording is turned off: it
 * deletes its visits and pushes the deletions. This server only stores.</p>
 *
 * @since 0.0.19
 */
@Service
public class VisitSynchronizationService {

    private final VisitRepository visitRepository;
    private final UserDataChangePublisher userDataChangePublisher;

    /**
     * Creates the service.
     *
     * @param visitRepository         persistence access for visits
     * @param userDataChangePublisher announces changes to the user's other browsers
     */
    public VisitSynchronizationService(
            final VisitRepository visitRepository,
            final UserDataChangePublisher userDataChangePublisher) {

        this.visitRepository = visitRepository;
        this.userDataChangePublisher = userDataChangePublisher;
    }

    /**
     * Stores a visit the client pushed, updating the owner's existing row or inserting a new one.
     *
     * <p>Every synchronized field is replaced, including {@code visitedAt}, which is stored as
     * the client sent it.</p>
     *
     * @param requestedVisitId identifier the client wants the visit stored under
     * @param profile          profile whose history the visit is in, already resolved against
     *                         the caller's own identifier
     * @param values           the fields to store, all of which are replaced
     * @return the stored entity, whose identifier differs from the requested one exactly when the
     *         requested one was already taken
     */
    @Transactional
    public VisitEntity upsertVisitFromPushedOperation(
            final UUID requestedVisitId,
            final ProfileEntity profile,
            final VisitSynchronizedValuesDto values) {

        final UUID ownerId = profile.getOwner().getId();
        final Optional<VisitEntity> existingVisit =
                visitRepository.findByIdAndOwnerId(requestedVisitId, ownerId);

        userDataChangePublisher.publishChangeFor(ownerId);

        if (existingVisit.isPresent()) {
            return applyValues(existingVisit.get(), profile, values);
        }
        return insertVisit(requestedVisitId, profile, values);
    }

    /**
     * Deletes a visit if the owner still has one under that identifier.
     *
     * @param visitId identifier of the visit to delete
     * @param ownerId identifier of the user that must own it
     * @return {@code true} when a row was deleted, {@code false} when there was nothing to delete
     */
    @Transactional
    public boolean deleteVisitFromPushedOperationIfPresent(
            final UUID visitId,
            final UUID ownerId) {

        final Optional<VisitEntity> existingVisit =
                visitRepository.findByIdAndOwnerId(visitId, ownerId);

        if (existingVisit.isEmpty()) {
            return false;
        }
        visitRepository.delete(existingVisit.get());
        userDataChangePublisher.publishChangeFor(ownerId);
        return true;
    }

    /**
     * Deletes an account's oldest visits beyond what its plan keeps, but never more than a push
     * added.
     *
     * <p>The same rule as closed tabs, for the same reasons (see
     * {@link com.kovospace.newtablinks.closedtab.services.ClosedTabSynchronizationService#trimHistoryToPlanLimit}):
     * the history never refuses, the newest entries across all profiles survive, and the number
     * kept is the plan's limit or the size before the push when that was larger, so a downgrade
     * does not cut a long history down at once.</p>
     *
     * @param ownerId               identifier of the account
     * @param historyLimit          how many entries the account's plan keeps
     * @param historySizeBeforePush how many it held before the push began
     * @return how many entries were deleted; zero when the account was within what it keeps
     */
    @Transactional
    public int trimHistoryToPlanLimit(
            final UUID ownerId,
            final int historyLimit,
            final long historySizeBeforePush) {

        final long entriesToKeep = Math.max(historyLimit, historySizeBeforePush);
        final List<UUID> newestFirst = visitRepository.findIdsByOwnerIdNewestFirst(ownerId);
        if (newestFirst.size() <= entriesToKeep) {
            return 0;
        }
        final List<UUID> oldestBeyondTheLimit =
                newestFirst.subList((int) entriesToKeep, newestFirst.size());
        visitRepository.deleteAllById(oldestBeyondTheLimit);
        userDataChangePublisher.publishChangeFor(ownerId);
        return oldestBeyondTheLimit.size();
    }

    /**
     * Inserts a visit, under the client's identifier when that identifier is still free.
     *
     * @param requestedVisitId identifier the client asked for
     * @param profile          profile whose history the visit is in
     * @param values           the fields to store
     * @return the inserted entity
     */
    private VisitEntity insertVisit(
            final UUID requestedVisitId,
            final ProfileEntity profile,
            final VisitSynchronizedValuesDto values) {

        final VisitEntity newVisit = new VisitEntity(
                profile,
                values.url(),
                values.title(),
                values.faviconUrl(),
                values.visitedAt(),
                values.deviceName());

        newVisit.setId(ClientAssignedIdentifierPolicy.chooseIdentifierForInsert(
                requestedVisitId, visitRepository::existsById));

        return visitRepository.save(newVisit);
    }

    /**
     * Overwrites every synchronized field of an existing visit.
     *
     * @param existingVisit the managed entity to update
     * @param profile       profile whose history the visit is in
     * @param values        the fields to store
     * @return the same entity, updated
     */
    private VisitEntity applyValues(
            final VisitEntity existingVisit,
            final ProfileEntity profile,
            final VisitSynchronizedValuesDto values) {

        existingVisit.setProfile(profile);
        existingVisit.setUrl(values.url());
        existingVisit.setTitle(values.title());
        existingVisit.setFaviconUrl(values.faviconUrl());
        existingVisit.setVisitedAt(values.visitedAt());
        existingVisit.setDeviceName(values.deviceName());
        return existingVisit;
    }
}
