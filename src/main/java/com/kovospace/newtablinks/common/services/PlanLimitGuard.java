package com.kovospace.newtablinks.common.services;

import com.kovospace.newtablinks.common.exceptions.PlanLimitReachedException;
import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.models.EffectivePlanLimits;
import com.kovospace.newtablinks.common.models.PlanLimit;
import com.kovospace.newtablinks.common.models.WorkspaceLocation;
import com.kovospace.newtablinks.environment.repositories.EnvironmentRepository;
import com.kovospace.newtablinks.group.repositories.GroupRepository;
import com.kovospace.newtablinks.link.repositories.LinkRepository;
import com.kovospace.newtablinks.profile.repositories.ProfileRepository;
import com.kovospace.newtablinks.subgroup.repositories.SubgroupRepository;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refuses an interactive (REST) write that the account's plan does not allow.
 *
 * <p>Three kinds of refusal, all HTTP 409 through {@link PlanLimitReachedException}:</p>
 * <ul>
 *   <li><strong>No slot left</strong> - one more profile, or one more workspace in a profile,
 *       than the plan's slots.</li>
 *   <li><strong>No slot held</strong> - any write into a profile or workspace that holds no
 *       synchronisation slot ({@link SynchronisationSlotReader}), or into its groups, subgroups
 *       and links. Without it, data left over after premium ends would keep synchronising its
 *       edits. Deleting such a profile or workspace itself stays allowed: it frees nothing
 *       anybody else holds, and data is never trapped.</li>
 *   <li><strong>Container full</strong> - one more group in a workspace, subgroup in a group or
 *       link in a workspace than the Fair Use cap of that container. Only growth is refused.</li>
 * </ul>
 *
 * <p>The sync push is judged on its end state instead, by {@link SyncPushLimitGuard}.</p>
 *
 * <p><strong>Concurrency.</strong> Every entry point first locks the account's row
 * ({@link UserRepository#findByIdForUpdate(UUID)}) until the caller's transaction ends, so a
 * count and the write after it are atomic against every other guarded write of the same account.
 * Hence {@link Propagation#MANDATORY} - outside a transaction the lock would be released before
 * the insert and protect nothing.</p>
 *
 * @since 0.0.16
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PlanLimitGuard {

    private static final String ACCOUNT_RESOURCE_NAME = "User";

    private final PlanLimitPolicy planLimitPolicy;
    private final SynchronisationSlotReader synchronisationSlotReader;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final EnvironmentRepository environmentRepository;
    private final GroupRepository groupRepository;
    private final SubgroupRepository subgroupRepository;
    private final LinkRepository linkRepository;

    /**
     * Creates the guard.
     *
     * @param planLimitPolicy           the account's limits and the wording of a refusal
     * @param synchronisationSlotReader which profiles and workspaces hold slots
     * @param userRepository            locks the account row
     * @param profileRepository         counts profiles
     * @param environmentRepository     counts workspaces of a profile
     * @param groupRepository           counts groups of a workspace
     * @param subgroupRepository        counts subgroups of a group
     * @param linkRepository            counts links of a workspace
     */
    public PlanLimitGuard(
            final PlanLimitPolicy planLimitPolicy,
            final SynchronisationSlotReader synchronisationSlotReader,
            final UserRepository userRepository,
            final ProfileRepository profileRepository,
            final EnvironmentRepository environmentRepository,
            final GroupRepository groupRepository,
            final SubgroupRepository subgroupRepository,
            final LinkRepository linkRepository) {

        this.planLimitPolicy = planLimitPolicy;
        this.synchronisationSlotReader = synchronisationSlotReader;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.environmentRepository = environmentRepository;
        this.groupRepository = groupRepository;
        this.subgroupRepository = subgroupRepository;
        this.linkRepository = linkRepository;
    }

    /**
     * Refuses one more profile when every profile slot of the account is taken.
     *
     * @param ownerId identifier of the account
     * @throws PlanLimitReachedException with {@link PlanLimit#PROFILES} when the account already
     *                                   holds the maximum or more
     * @throws ResourceNotFoundException when the account does not exist
     */
    public void requireRoomForAnotherProfile(final UUID ownerId) {
        final EffectivePlanLimits limits = lockAndReadLimits(ownerId);
        requireRoomForOneMore(limits, PlanLimit.PROFILES, profileRepository.countByOwnerId(ownerId));
    }

    /**
     * Refuses a write into a profile that holds no slot.
     *
     * @param ownerId   identifier of the account
     * @param profileId identifier of the profile, already resolved for {@code ownerId}
     * @throws PlanLimitReachedException with {@link PlanLimit#PROFILES} when it holds no slot
     * @throws ResourceNotFoundException when the account does not exist
     */
    public void requireProfileHoldsSlot(final UUID ownerId, final UUID profileId) {
        requireProfileHoldsSlot(lockAndReadLimits(ownerId), ownerId, profileId);
    }

    /**
     * Refuses one more workspace in a profile without a slot, or with every workspace slot taken.
     *
     * @param ownerId   identifier of the account
     * @param profileId identifier of the profile the workspace would be filed under
     * @throws PlanLimitReachedException with {@link PlanLimit#PROFILES} when the profile holds no
     *                                   slot, {@link PlanLimit#WORKSPACES_PER_PROFILE} when it is
     *                                   full
     * @throws ResourceNotFoundException when the account does not exist
     */
    public void requireRoomForAnotherWorkspace(final UUID ownerId, final UUID profileId) {
        final EffectivePlanLimits limits = lockAndReadLimits(ownerId);
        requireProfileHoldsSlot(limits, ownerId, profileId);
        requireRoomForOneMore(limits, PlanLimit.WORKSPACES_PER_PROFILE,
                environmentRepository.countByProfileId(profileId));
    }

    /**
     * Refuses a write into a workspace that holds no slot - or into its groups, subgroups, links.
     *
     * @param workspace where the workspace sits
     * @throws PlanLimitReachedException with {@link PlanLimit#PROFILES} when its profile holds no
     *                                   slot, {@link PlanLimit#WORKSPACES_PER_PROFILE} when it
     *                                   holds none itself
     * @throws ResourceNotFoundException when the account does not exist
     */
    public void requireWorkspaceHoldsSlot(final WorkspaceLocation workspace) {
        requireWorkspaceHoldsSlot(lockAndReadLimits(workspace.ownerId()), workspace);
    }

    /**
     * Refuses one more group in a workspace without a slot, or at its Fair Use cap.
     *
     * @param workspace where the workspace sits
     * @throws PlanLimitReachedException naming the slot, or {@link PlanLimit#GROUPS_PER_WORKSPACE}
     * @throws ResourceNotFoundException when the account does not exist
     */
    public void requireRoomForAnotherGroup(final WorkspaceLocation workspace) {
        final EffectivePlanLimits limits = lockAndReadLimits(workspace.ownerId());
        requireWorkspaceHoldsSlot(limits, workspace);
        requireRoomForOneMore(limits, PlanLimit.GROUPS_PER_WORKSPACE,
                groupRepository.countByEnvironmentId(workspace.workspaceId()));
    }

    /**
     * Refuses one more subgroup in a group of a workspace without a slot, or at its Fair Use cap.
     *
     * @param workspace where the group's workspace sits
     * @param groupId   identifier of the group, already resolved for the account
     * @throws PlanLimitReachedException naming the slot, or {@link PlanLimit#SUBGROUPS_PER_GROUP}
     * @throws ResourceNotFoundException when the account does not exist
     */
    public void requireRoomForAnotherSubgroup(
            final WorkspaceLocation workspace,
            final UUID groupId) {

        final EffectivePlanLimits limits = lockAndReadLimits(workspace.ownerId());
        requireWorkspaceHoldsSlot(limits, workspace);
        requireRoomForOneMore(limits, PlanLimit.SUBGROUPS_PER_GROUP,
                subgroupRepository.countByParentGroupId(groupId));
    }

    /**
     * Refuses one more link in a workspace without a slot, or at its Fair Use cap.
     *
     * @param workspace where the workspace sits
     * @throws PlanLimitReachedException naming the slot, or {@link PlanLimit#LINKS_PER_WORKSPACE}
     * @throws ResourceNotFoundException when the account does not exist
     */
    public void requireRoomForAnotherLink(final WorkspaceLocation workspace) {
        final EffectivePlanLimits limits = lockAndReadLimits(workspace.ownerId());
        requireWorkspaceHoldsSlot(limits, workspace);
        requireRoomForOneMore(limits, PlanLimit.LINKS_PER_WORKSPACE,
                linkRepository.countByEnvironmentId(workspace.workspaceId()));
    }

    /**
     * Refuses a write into a profile without a slot, under limits already read.
     *
     * @param limits    the account's limits
     * @param ownerId   identifier of the account
     * @param profileId identifier of the profile
     * @throws PlanLimitReachedException with {@link PlanLimit#PROFILES}
     */
    private void requireProfileHoldsSlot(
            final EffectivePlanLimits limits,
            final UUID ownerId,
            final UUID profileId) {

        if (!synchronisationSlotReader.findSlotHoldingProfileIds(ownerId, limits)
                .contains(profileId)) {
            throw planLimitPolicy.refusalOf(limits, PlanLimit.PROFILES);
        }
    }

    /**
     * Refuses a write into a workspace without a slot, under limits already read.
     *
     * @param limits    the account's limits
     * @param workspace where the workspace sits
     * @throws PlanLimitReachedException naming the profile or the workspace slot
     */
    private void requireWorkspaceHoldsSlot(
            final EffectivePlanLimits limits,
            final WorkspaceLocation workspace) {

        requireProfileHoldsSlot(limits, workspace.ownerId(), workspace.profileId());
        if (!synchronisationSlotReader
                .findSlotHoldingWorkspaceIdsOfProfile(workspace.profileId(), limits)
                .contains(workspace.workspaceId())) {
            throw planLimitPolicy.refusalOf(limits, PlanLimit.WORKSPACES_PER_PROFILE);
        }
    }

    /**
     * Refuses adding one record to a collection of the given size.
     *
     * @param limits       the account's limits
     * @param limit        the limit
     * @param currentCount the collection's size now
     * @throws PlanLimitReachedException when one more would exceed the limit
     */
    private void requireRoomForOneMore(
            final EffectivePlanLimits limits,
            final PlanLimit limit,
            final long currentCount) {

        if (currentCount + 1 > limits.maximumFor(limit)) {
            throw planLimitPolicy.refusalOf(limits, limit);
        }
    }

    /**
     * Takes the account's row lock for the rest of the caller's transaction, then reads the
     * limits that hold for it.
     *
     * @param ownerId identifier of the account
     * @return its limits
     * @throws ResourceNotFoundException when the account does not exist
     */
    private EffectivePlanLimits lockAndReadLimits(final UUID ownerId) {
        userRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_RESOURCE_NAME, ownerId));
        return planLimitPolicy.effectiveLimitsFor(ownerId);
    }
}
