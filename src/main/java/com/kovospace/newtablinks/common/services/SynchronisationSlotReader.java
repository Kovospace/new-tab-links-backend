package com.kovospace.newtablinks.common.services;

import com.kovospace.newtablinks.common.models.EffectivePlanLimits;
import com.kovospace.newtablinks.common.models.PlanLimit;
import com.kovospace.newtablinks.environment.repositories.EnvironmentRepository;
import com.kovospace.newtablinks.profile.repositories.ProfileRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * Tells which profiles and workspaces of an account hold a synchronisation slot.
 *
 * <p>The plan's profile and workspace limits are slots, and "first" means first to take one: the
 * order is the server's {@code createdAt}, stamped when the record first reached the server, with
 * ties broken by identifier. When a record was created or last edited on a device is irrelevant.
 * </p>
 *
 * <ul>
 *   <li>Profiles: the account's first {@link PlanLimit#PROFILES} profiles hold slots.</li>
 *   <li>Workspaces: the first {@link PlanLimit#WORKSPACES_PER_PROFILE} workspaces of a
 *       slot-holding profile hold slots; a workspace under a profile without a slot holds
 *       none.</li>
 * </ul>
 *
 * <p>The extension works the same answer out from {@code GET /api/v1/profiles} and
 * {@code GET /api/v1/environments} ({@code createdAt}, then {@code id} as a lowercase string) and
 * pushes only what holds a slot; this side exists to validate, so that a race between two
 * devices taking the last free slot is refused rather than stored.</p>
 *
 * @since 0.0.18
 */
@Service
public class SynchronisationSlotReader {

    private final ProfileRepository profileRepository;
    private final EnvironmentRepository environmentRepository;

    /**
     * Creates the reader.
     *
     * @param profileRepository     lists profiles in slot order
     * @param environmentRepository lists environments in slot order
     */
    public SynchronisationSlotReader(
            final ProfileRepository profileRepository,
            final EnvironmentRepository environmentRepository) {

        this.profileRepository = profileRepository;
        this.environmentRepository = environmentRepository;
    }

    /**
     * Returns the profiles of an account that hold a slot.
     *
     * @param ownerId identifier of the account
     * @param limits  the limits that hold for it now
     * @return the identifiers of its first {@link PlanLimit#PROFILES} profiles; empty when it has
     *         none
     */
    public Set<UUID> findSlotHoldingProfileIds(
            final UUID ownerId,
            final EffectivePlanLimits limits) {

        return Set.copyOf(profileRepository.findIdsOfOwnerInSlotOrder(
                ownerId, Limit.of(limits.maximumFor(PlanLimit.PROFILES))));
    }

    /**
     * Returns the workspaces of one profile that hold a slot, assuming the profile holds one.
     *
     * <p>The caller decides whether the profile itself holds a slot; under a profile without one
     * no workspace does, whatever this returns.</p>
     *
     * @param profileId identifier of the profile, already resolved for its owner
     * @param limits    the limits that hold for its account now
     * @return the identifiers of its first {@link PlanLimit#WORKSPACES_PER_PROFILE} workspaces
     */
    public Set<UUID> findSlotHoldingWorkspaceIdsOfProfile(
            final UUID profileId,
            final EffectivePlanLimits limits) {

        return Set.copyOf(environmentRepository.findIdsOfProfileInSlotOrder(
                profileId, Limit.of(limits.maximumFor(PlanLimit.WORKSPACES_PER_PROFILE))));
    }
}
