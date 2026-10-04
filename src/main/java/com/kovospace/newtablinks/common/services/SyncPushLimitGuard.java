package com.kovospace.newtablinks.common.services;

import com.kovospace.newtablinks.closedtab.repositories.ClosedTabRepository;
import com.kovospace.newtablinks.common.exceptions.PlanLimitReachedException;
import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.models.ContainerItemCount;
import com.kovospace.newtablinks.common.models.EffectivePlanLimits;
import com.kovospace.newtablinks.common.models.PlanLimit;
import com.kovospace.newtablinks.common.models.PushFootprint;
import com.kovospace.newtablinks.common.models.PushLimitBaseline;
import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import com.kovospace.newtablinks.environment.repositories.EnvironmentRepository;
import com.kovospace.newtablinks.group.repositories.GroupRepository;
import com.kovospace.newtablinks.link.repositories.LinkRepository;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.repositories.ProfileRepository;
import com.kovospace.newtablinks.subgroup.repositories.SubgroupRepository;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Judges a whole sync push against the account's plan, on its end state.
 *
 * <p>A device sends its upserts before its deletions, so a per-operation check would refuse a
 * push that replaces a record at a limit. Instead the push takes {@link #captureBaselineBeforeBatch}
 * before its first operation and calls {@link #requireEndStateWithinLimits} after its last,
 * before commit; throwing rolls the whole push back. The backend never accepts half a push.</p>
 *
 * <p>The end state is refused when:</p>
 * <ul>
 *   <li>a profile the push wrote into holds no slot at the end - including one it created
 *       beyond the profile slots ({@link PlanLimit#PROFILES});</li>
 *   <li>a workspace the push wrote into lies under a profile without a slot
 *       ({@link PlanLimit#PROFILES}), or holds no slot of its own at the end - including one it
 *       created beyond its profile's workspace slots ({@link PlanLimit#WORKSPACES_PER_PROFILE});
 *       </li>
 *   <li>a workspace's groups, a group's subgroups or a workspace's links end above their cap
 *       <em>and</em> larger than they started - growth only.</li>
 * </ul>
 *
 * <p>With the extension working the slots out itself first, a refusal here means a race: two
 * devices took the last free slot at once. The loser re-reads the lists and pushes again without
 * what lost, which cannot loop - its next push no longer contains it.</p>
 *
 * <p>Runs inside the push's transaction ({@link Propagation#MANDATORY}) under the account's row
 * lock, for the reason given on {@link PlanLimitGuard}.</p>
 *
 * @since 0.0.18
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class SyncPushLimitGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncPushLimitGuard.class);

    private static final String ACCOUNT_RESOURCE_NAME = "User";

    private final PlanLimitPolicy planLimitPolicy;
    private final SynchronisationSlotReader synchronisationSlotReader;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final EnvironmentRepository environmentRepository;
    private final ContainerCounter containerCounter;

    /**
     * Creates the guard.
     *
     * @param planLimitPolicy           the account's limits and the wording of a refusal
     * @param synchronisationSlotReader which profiles and workspaces hold slots
     * @param userRepository            locks the account row
     * @param profileRepository         resolves the profiles a push wrote into
     * @param environmentRepository     resolves the workspaces a push wrote into
     * @param groupRepository           counts groups per workspace
     * @param subgroupRepository        counts subgroups per group
     * @param linkRepository            counts links per workspace
     * @param closedTabRepository       counts closed tabs
     */
    public SyncPushLimitGuard(
            final PlanLimitPolicy planLimitPolicy,
            final SynchronisationSlotReader synchronisationSlotReader,
            final UserRepository userRepository,
            final ProfileRepository profileRepository,
            final EnvironmentRepository environmentRepository,
            final GroupRepository groupRepository,
            final SubgroupRepository subgroupRepository,
            final LinkRepository linkRepository,
            final ClosedTabRepository closedTabRepository) {

        this.planLimitPolicy = planLimitPolicy;
        this.synchronisationSlotReader = synchronisationSlotReader;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.environmentRepository = environmentRepository;
        this.containerCounter = new ContainerCounter(
                groupRepository, subgroupRepository, linkRepository, closedTabRepository);
    }

    /**
     * Locks the account and records its limits and container sizes, before a push applies
     * anything.
     *
     * @param ownerId identifier of the account
     * @return what the push will be judged against
     * @throws ResourceNotFoundException when the account does not exist
     */
    public PushLimitBaseline captureBaselineBeforeBatch(final UUID ownerId) {
        userRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_RESOURCE_NAME, ownerId));
        return new PushLimitBaseline(
                planLimitPolicy.effectiveLimitsFor(ownerId),
                containerCounter.groupsPerWorkspace(ownerId),
                containerCounter.subgroupsPerGroup(ownerId),
                containerCounter.linksPerWorkspace(ownerId),
                containerCounter.closedTabs(ownerId));
    }

    /**
     * Refuses a push whose end state the plan does not allow.
     *
     * <p>Called after the last operation; the queries flush the push's writes first, so they
     * judge what the push would commit.</p>
     *
     * @param ownerId   identifier of the account
     * @param baseline  what {@link #captureBaselineBeforeBatch(UUID)} returned for this push
     * @param footprint the profiles and workspaces the push wrote into
     * @throws PlanLimitReachedException naming the first breach, checked in the order profiles,
     *                                   workspaces, groups, subgroups, links
     */
    public void requireEndStateWithinLimits(
            final UUID ownerId,
            final PushLimitBaseline baseline,
            final PushFootprint footprint) {

        final EffectivePlanLimits limits = baseline.limits();
        final Set<UUID> slotProfiles =
                synchronisationSlotReader.findSlotHoldingProfileIds(ownerId, limits);

        refuseWritesIntoProfilesWithoutSlot(ownerId, footprint, slotProfiles, limits);
        refuseWritesIntoWorkspacesWithoutSlot(ownerId, footprint, slotProfiles, limits);
        refuseGrowthPastCap(limits, PlanLimit.GROUPS_PER_WORKSPACE,
                baseline.groupCountsByWorkspaceId(), containerCounter.groupsPerWorkspace(ownerId));
        refuseGrowthPastCap(limits, PlanLimit.SUBGROUPS_PER_GROUP,
                baseline.subgroupCountsByGroupId(), containerCounter.subgroupsPerGroup(ownerId));
        refuseGrowthPastCap(limits, PlanLimit.LINKS_PER_WORKSPACE,
                baseline.linkCountsByWorkspaceId(), containerCounter.linksPerWorkspace(ownerId));
    }

    /**
     * Refuses the push when a profile it wrote into, and that still exists, holds no slot.
     *
     * @param ownerId      identifier of the account
     * @param footprint    what the push wrote into
     * @param slotProfiles the profiles holding a slot at the end
     * @param limits       the account's limits
     * @throws PlanLimitReachedException with {@link PlanLimit#PROFILES}
     */
    private void refuseWritesIntoProfilesWithoutSlot(
            final UUID ownerId,
            final PushFootprint footprint,
            final Set<UUID> slotProfiles,
            final EffectivePlanLimits limits) {

        final boolean wroteIntoProfileWithoutSlot =
                profileRepository.findAllById(footprint.profileIds()).stream()
                        .filter(profile -> profile.getOwner().getId().equals(ownerId))
                        .map(ProfileEntity::getId)
                        .anyMatch(profileId -> !slotProfiles.contains(profileId));
        if (wroteIntoProfileWithoutSlot) {
            LOGGER.info("Refused a sync push of account {} writing into a profile without a slot",
                    ownerId);
            throw planLimitPolicy.refusalOf(limits, PlanLimit.PROFILES);
        }
    }

    /**
     * Refuses the push when a workspace it wrote into, and that still exists, holds no slot.
     *
     * @param ownerId      identifier of the account
     * @param footprint    what the push wrote into
     * @param slotProfiles the profiles holding a slot at the end
     * @param limits       the account's limits
     * @throws PlanLimitReachedException with {@link PlanLimit#PROFILES} when the workspace's
     *                                   profile holds no slot, otherwise
     *                                   {@link PlanLimit#WORKSPACES_PER_PROFILE}
     */
    private void refuseWritesIntoWorkspacesWithoutSlot(
            final UUID ownerId,
            final PushFootprint footprint,
            final Set<UUID> slotProfiles,
            final EffectivePlanLimits limits) {

        final Map<UUID, Set<UUID>> slotWorkspacesByProfileId = new HashMap<>();
        final List<EnvironmentEntity> writtenWorkspaces =
                environmentRepository.findAllById(footprint.workspaceIds()).stream()
                        .filter(workspace -> workspace.getOwner().getId().equals(ownerId))
                        .toList();

        for (final EnvironmentEntity workspace : writtenWorkspaces) {
            final UUID profileId = workspace.getProfile().getId();
            if (!slotProfiles.contains(profileId)) {
                throw planLimitPolicy.refusalOf(limits, PlanLimit.PROFILES);
            }
            final Set<UUID> slotWorkspaces = slotWorkspacesByProfileId.computeIfAbsent(profileId,
                    id -> synchronisationSlotReader.findSlotHoldingWorkspaceIdsOfProfile(id, limits));
            if (!slotWorkspaces.contains(workspace.getId())) {
                LOGGER.info("Refused a sync push of account {} writing into workspace {} without "
                        + "a slot", ownerId, workspace.getId());
                throw planLimitPolicy.refusalOf(limits, PlanLimit.WORKSPACES_PER_PROFILE);
            }
        }
    }

    /**
     * Refuses the push when any container ended above its cap and larger than it started.
     *
     * @param limits       the account's limits
     * @param limit        the cap
     * @param countsBefore the containers' sizes before the push
     * @param countsAfter  their sizes after it
     * @throws PlanLimitReachedException naming the cap
     */
    private void refuseGrowthPastCap(
            final EffectivePlanLimits limits,
            final PlanLimit limit,
            final Map<UUID, Long> countsBefore,
            final Map<UUID, Long> countsAfter) {

        final int maximum = limits.maximumFor(limit);
        final boolean grewPastCap = countsAfter.entrySet().stream().anyMatch(entry ->
                entry.getValue() > maximum
                        && entry.getValue() > countsBefore.getOrDefault(entry.getKey(), 0L));
        if (grewPastCap) {
            throw planLimitPolicy.refusalOf(limits, limit);
        }
    }

    /**
     * Counts an account's records per container, for the baseline and the end state.
     *
     * @param groupRepository     counts groups per workspace
     * @param subgroupRepository  counts subgroups per group
     * @param linkRepository      counts links per workspace
     * @param closedTabRepository counts closed tabs
     */
    private record ContainerCounter(
            GroupRepository groupRepository,
            SubgroupRepository subgroupRepository,
            LinkRepository linkRepository,
            ClosedTabRepository closedTabRepository) {

        /** Groups per workspace of the account; a workspace without any is absent. */
        Map<UUID, Long> groupsPerWorkspace(final UUID ownerId) {
            return toMap(groupRepository.countGroupsPerEnvironmentOfOwner(ownerId));
        }

        /** Subgroups per group of the account; a group without any is absent. */
        Map<UUID, Long> subgroupsPerGroup(final UUID ownerId) {
            return toMap(subgroupRepository.countSubgroupsPerGroupOfOwner(ownerId));
        }

        /** Links per workspace of the account; a workspace without any is absent. */
        Map<UUID, Long> linksPerWorkspace(final UUID ownerId) {
            return toMap(linkRepository.countLinksPerEnvironmentOfOwner(ownerId));
        }

        /** The account's closed-tab history entries. */
        long closedTabs(final UUID ownerId) {
            return closedTabRepository.countByProfileOwnerId(ownerId);
        }

        /** Turns per-container counts into a map keyed by container. */
        private static Map<UUID, Long> toMap(final List<ContainerItemCount> counts) {
            return counts.stream().collect(Collectors.toMap(
                    ContainerItemCount::containerId, ContainerItemCount::itemCount));
        }
    }
}
