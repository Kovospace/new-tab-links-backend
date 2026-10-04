package com.kovospace.newtablinks.common.services;

import com.kovospace.newtablinks.common.config.FairUseLimitProperties;
import com.kovospace.newtablinks.common.exceptions.FairUseLimitReachedException;
import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.models.FairUseCounts;
import com.kovospace.newtablinks.common.models.FairUseLimit;
import com.kovospace.newtablinks.environment.repositories.EnvironmentRepository;
import com.kovospace.newtablinks.link.models.WorkspaceLinkCount;
import com.kovospace.newtablinks.link.repositories.LinkRepository;
import com.kovospace.newtablinks.profile.repositories.ProfileRepository;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refuses a write that would grow an account past a cap of the published Fair Use Policy.
 *
 * <p>Three caps refuse: {@link FairUseLimit#PROFILES}, {@link FairUseLimit#WORKSPACES} and
 * {@link FairUseLimit#LINKS_PER_WORKSPACE}. The fourth, closed-tab history, never refuses - its
 * oldest entries are trimmed instead (see {@code ClosedTabSynchronizationService}).</p>
 *
 * <p>Two kinds of caller, two shapes of check:</p>
 * <ul>
 *   <li>An interactive create asks before it stores one record:
 *       {@link #requireRoomForAnotherProfile}, {@link #requireRoomForAnotherWorkspace},
 *       {@link #requireRoomForAnotherLinkInWorkspace}.</li>
 *   <li>The sync push judges the batch as a whole. A device sends all of its upserts before
 *       its deletions, so a per-operation check would refuse a push that replaces a record at a
 *       cap. The push takes {@link #captureCountsBeforeBatch} first and calls
 *       {@link #requireBatchDidNotGrowPastCaps} after its last operation, before commit.</li>
 * </ul>
 *
 * <p><strong>Only growth is refused.</strong> A collection is in breach only when it ends above
 * its cap <em>and</em> larger than it started, so data already over a cap is never deleted and
 * stays editable, and a push that edits, moves within or deletes from it goes through.</p>
 *
 * <p><strong>Concurrency.</strong> Every entry point first locks the account's row
 * ({@link UserRepository#findByIdForUpdate(UUID)}) until the caller's transaction ends, so a
 * count and the writes after it are atomic against every other guarded write of the same
 * account: two parallel requests at one below a cap queue on the lock, and the second sees the
 * first one's row. Hence {@link Propagation#MANDATORY} - outside a transaction the lock would be
 * released before the insert and protect nothing.</p>
 *
 * @since 0.0.16
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class FairUseLimitGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(FairUseLimitGuard.class);

    private static final String ACCOUNT_RESOURCE_NAME = "User";

    private final FairUseLimitProperties fairUseLimitProperties;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final EnvironmentRepository environmentRepository;
    private final LinkRepository linkRepository;

    /**
     * Creates the guard.
     *
     * @param fairUseLimitProperties the caps
     * @param userRepository         locks the account row
     * @param profileRepository      counts profiles
     * @param environmentRepository  counts environments
     * @param linkRepository         counts links
     */
    public FairUseLimitGuard(
            final FairUseLimitProperties fairUseLimitProperties,
            final UserRepository userRepository,
            final ProfileRepository profileRepository,
            final EnvironmentRepository environmentRepository,
            final LinkRepository linkRepository) {

        this.fairUseLimitProperties = fairUseLimitProperties;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.environmentRepository = environmentRepository;
        this.linkRepository = linkRepository;
    }

    /**
     * Refuses one more profile when the account is already at its cap.
     *
     * @param ownerId identifier of the account
     * @throws FairUseLimitReachedException when the account holds the maximum or more
     * @throws ResourceNotFoundException    when the account does not exist
     */
    public void requireRoomForAnotherProfile(final UUID ownerId) {
        lockAccountAgainstConcurrentGrowth(ownerId);
        requireRoomForOneMore(FairUseLimit.PROFILES, profileRepository.countByOwnerId(ownerId));
    }

    /**
     * Refuses one more workspace (environment) when the account is already at its cap.
     *
     * @param ownerId identifier of the account
     * @throws FairUseLimitReachedException when the account holds the maximum or more
     * @throws ResourceNotFoundException    when the account does not exist
     */
    public void requireRoomForAnotherWorkspace(final UUID ownerId) {
        lockAccountAgainstConcurrentGrowth(ownerId);
        requireRoomForOneMore(
                FairUseLimit.WORKSPACES, environmentRepository.countByOwnerId(ownerId));
    }

    /**
     * Refuses one more link in a workspace that is already at its cap.
     *
     * @param ownerId     identifier of the account
     * @param workspaceId the environment the link arrives in, already resolved for
     *                    {@code ownerId}
     * @throws FairUseLimitReachedException when the workspace holds the maximum or more
     * @throws ResourceNotFoundException    when the account does not exist
     */
    public void requireRoomForAnotherLinkInWorkspace(final UUID ownerId, final UUID workspaceId) {
        lockAccountAgainstConcurrentGrowth(ownerId);
        requireRoomForOneMore(
                FairUseLimit.LINKS_PER_WORKSPACE, linkRepository.countByEnvironmentId(workspaceId));
    }

    /**
     * Locks the account and records its counts, before a sync push applies anything.
     *
     * @param ownerId identifier of the account
     * @return the counts as they stand before the batch
     * @throws ResourceNotFoundException when the account does not exist
     */
    public FairUseCounts captureCountsBeforeBatch(final UUID ownerId) {
        lockAccountAgainstConcurrentGrowth(ownerId);
        return countEverything(ownerId);
    }

    /**
     * Refuses a sync push that left any capped collection both above its cap and larger than
     * before the push.
     *
     * <p>Called after the last operation and before commit; the queries flush the batch's
     * writes first, so they count what the push would commit. Throwing rolls the whole push
     * back.</p>
     *
     * @param ownerId      identifier of the account
     * @param countsBefore what {@link #captureCountsBeforeBatch(UUID)} returned for this push
     * @throws FairUseLimitReachedException naming the first cap in breach, checked in the order
     *                                      profiles, workspaces, links per workspace
     */
    public void requireBatchDidNotGrowPastCaps(
            final UUID ownerId,
            final FairUseCounts countsBefore) {

        final FairUseCounts countsAfter = countEverything(ownerId);
        refuseGrowthPastCap(FairUseLimit.PROFILES,
                countsBefore.profileCount(), countsAfter.profileCount(), ownerId);
        refuseGrowthPastCap(FairUseLimit.WORKSPACES,
                countsBefore.workspaceCount(), countsAfter.workspaceCount(), ownerId);
        countsAfter.linkCountsByWorkspaceId().forEach((workspaceId, linkCountAfter) ->
                refuseGrowthPastCap(FairUseLimit.LINKS_PER_WORKSPACE,
                        countsBefore.linkCountOf(workspaceId), linkCountAfter, ownerId));
    }

    /**
     * Counts every capped collection of an account.
     *
     * @param ownerId identifier of the account
     * @return the counts
     */
    private FairUseCounts countEverything(final UUID ownerId) {
        final Map<UUID, Long> linkCountsByWorkspaceId =
                linkRepository.countLinksPerEnvironmentOfOwner(ownerId).stream()
                        .collect(Collectors.toMap(
                                WorkspaceLinkCount::environmentId, WorkspaceLinkCount::linkCount));
        return new FairUseCounts(
                profileRepository.countByOwnerId(ownerId),
                environmentRepository.countByOwnerId(ownerId),
                linkCountsByWorkspaceId);
    }

    /**
     * Refuses adding one record to a collection of the given size.
     *
     * @param limit        the cap
     * @param currentCount the collection's size now
     * @throws FairUseLimitReachedException when one more would exceed the cap
     */
    private void requireRoomForOneMore(final FairUseLimit limit, final long currentCount) {
        final int maximum = fairUseLimitProperties.maximumFor(limit);
        if (currentCount + 1 > maximum) {
            LOGGER.info("Refused a write past the fair use cap {} of {}", limit, maximum);
            throw new FairUseLimitReachedException(limit, maximum);
        }
    }

    /**
     * Refuses a collection that ended above its cap and larger than it started.
     *
     * @param limit       the cap
     * @param countBefore the collection's size before the batch
     * @param countAfter  its size after the batch
     * @param ownerId     identifier of the account, for the log
     * @throws FairUseLimitReachedException when the batch grew it past the cap
     */
    private void refuseGrowthPastCap(
            final FairUseLimit limit,
            final long countBefore,
            final long countAfter,
            final UUID ownerId) {

        final int maximum = fairUseLimitProperties.maximumFor(limit);
        if (countAfter > maximum && countAfter > countBefore) {
            LOGGER.info("Refused a sync push growing account {} from {} to {} past the fair use "
                    + "cap {} of {}", ownerId, countBefore, countAfter, limit, maximum);
            throw new FairUseLimitReachedException(limit, maximum);
        }
    }

    /**
     * Takes the account's row lock for the rest of the caller's transaction.
     *
     * @param ownerId identifier of the account
     * @throws ResourceNotFoundException when the account does not exist
     */
    private void lockAccountAgainstConcurrentGrowth(final UUID ownerId) {
        userRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_RESOURCE_NAME, ownerId));
    }
}
