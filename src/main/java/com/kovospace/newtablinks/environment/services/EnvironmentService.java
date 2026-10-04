package com.kovospace.newtablinks.environment.services;

import com.kovospace.newtablinks.common.exceptions.FairUseLimitReachedException;
import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.services.FairUseLimitGuard;
import com.kovospace.newtablinks.common.services.HierarchyDeletionService;
import com.kovospace.newtablinks.common.utils.DisplayPositionCalculator;
import com.kovospace.newtablinks.environment.dtos.EnvironmentDto;
import com.kovospace.newtablinks.environment.dtos.EnvironmentSaveRequestDto;
import com.kovospace.newtablinks.environment.mappers.EnvironmentMapper;
import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import com.kovospace.newtablinks.environment.repositories.EnvironmentRepository;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.services.ProfileService;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business operations on environments.
 *
 * @since 0.0.1
 */
@Service
public class EnvironmentService {

    private static final String RESOURCE_NAME = "Environment";

    private final EnvironmentRepository environmentRepository;
    private final EnvironmentMapper environmentMapper;
    private final ProfileService profileService;
    private final UserDataChangePublisher userDataChangePublisher;
    private final HierarchyDeletionService hierarchyDeletionService;
    private final FairUseLimitGuard fairUseLimitGuard;

    /**
     * Creates the service.
     *
     * @param environmentRepository   persistence access for environments
     * @param environmentMapper       converter to the client facing shape
     * @param profileService          resolves the profile an environment is filed under, and with
     *                                it the owner
     * @param userDataChangePublisher  announces changes to the user's other browsers
     * @param hierarchyDeletionService removes a record together with everything beneath it
     * @param fairUseLimitGuard        refuses a workspace beyond the Fair Use Policy cap
     */
    public EnvironmentService(
            final EnvironmentRepository environmentRepository,
            final EnvironmentMapper environmentMapper,
            final ProfileService profileService,
            final UserDataChangePublisher userDataChangePublisher,
            final HierarchyDeletionService hierarchyDeletionService,
            final FairUseLimitGuard fairUseLimitGuard) {

        this.environmentRepository = environmentRepository;
        this.environmentMapper = environmentMapper;
        this.profileService = profileService;
        this.userDataChangePublisher = userDataChangePublisher;
        this.hierarchyDeletionService = hierarchyDeletionService;
        this.fairUseLimitGuard = fairUseLimitGuard;
    }

    /**
     * Lists a user's environments in display order.
     *
     * @param ownerId identifier of the owning user
     * @return the owner's environments, empty when there are none
     */
    @Transactional(readOnly = true)
    public List<EnvironmentDto> findEnvironmentsByOwner(final UUID ownerId) {
        return environmentMapper.toDtoList(
                environmentRepository.findAllByOwnerIdOrderByPositionAsc(ownerId));
    }

    /**
     * Returns a single environment belonging to the given owner.
     *
     * @param environmentId identifier of the environment
     * @param ownerId       identifier of the user that must own it
     * @return the environment
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional(readOnly = true)
    public EnvironmentDto findEnvironmentById(final UUID environmentId, final UUID ownerId) {
        return environmentMapper.toDto(getRequiredEnvironmentEntity(environmentId, ownerId));
    }

    /**
     * Creates an environment inside a profile and appends it after the owner's existing ones.
     *
     * <p>The profile is resolved with the caller's own identifier, so an environment can only
     * ever be filed under a profile the caller owns; the entity then takes its owner from that
     * profile, which is what keeps the two from disagreeing.</p>
     *
     * @param saveRequest the environment to create
     * @param ownerId     identifier of the user it is created for, taken from the access token
     * @return the created environment, including its assigned identifier and position
     * @throws ResourceNotFoundException    when the named profile does not exist or is not theirs
     * @throws FairUseLimitReachedException when the account already holds as many workspaces as
     *                                      the Fair Use Policy allows
     */
    @Transactional
    public EnvironmentDto createEnvironment(
            final EnvironmentSaveRequestDto saveRequest,
            final UUID ownerId) {

        final ProfileEntity parentProfile =
                profileService.getRequiredProfileEntity(saveRequest.profileId(), ownerId);
        fairUseLimitGuard.requireRoomForAnotherWorkspace(ownerId);

        final int position = DisplayPositionCalculator.calculatePositionForAppendedItem(
                environmentRepository.findHighestPositionByOwnerId(ownerId));

        final EnvironmentEntity newEnvironment = new EnvironmentEntity(
                parentProfile, saveRequest.name(), saveRequest.description(), position);

        userDataChangePublisher.publishChangeFor(ownerId);
        return environmentMapper.toDto(environmentRepository.save(newEnvironment));
    }

    /**
     * Renames an existing environment and, if asked, files it under a different profile.
     *
     * <p>The owner is not reassigned: moving an environment between users is not a rename and
     * would need its own operation. Moving it between profiles is a rename-sized change, and it
     * cannot change the owner either, because the target profile is resolved with the caller's
     * own identifier.</p>
     *
     * @param environmentId identifier of the environment to update
     * @param saveRequest   the values to store
     * @param ownerId       identifier of the user that must own it
     * @return the updated environment
     * @throws ResourceNotFoundException when the environment or the named profile does not exist
     *                                   or belongs to somebody else
     */
    @Transactional
    public EnvironmentDto updateEnvironment(
            final UUID environmentId,
            final EnvironmentSaveRequestDto saveRequest,
            final UUID ownerId) {

        final EnvironmentEntity existingEnvironment =
                getRequiredEnvironmentEntity(environmentId, ownerId);

        existingEnvironment.setProfile(
                profileService.getRequiredProfileEntity(saveRequest.profileId(), ownerId));
        existingEnvironment.setName(saveRequest.name());
        existingEnvironment.setDescription(saveRequest.description());

        userDataChangePublisher.publishChangeFor(ownerId);
        return environmentMapper.toDto(existingEnvironment);
    }

    /**
     * Deletes an environment and everything inside it: its groups, and every subgroup and
     * link in those.
     *
     * @param environmentId identifier of the environment to delete
     * @param ownerId       identifier of the user that must own it
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional
    public void deleteEnvironment(final UUID environmentId, final UUID ownerId) {
        hierarchyDeletionService.deleteEnvironmentWithDescendants(
                getRequiredEnvironmentEntity(environmentId, ownerId));
        userDataChangePublisher.publishChangeFor(ownerId);
    }

    /**
     * Loads an environment entity for another service in this application, enforcing ownership.
     *
     * <p>A row owned by somebody else is reported as missing rather than as forbidden, so that
     * the API never confirms that another user's identifier exists.</p>
     *
     * @param environmentId identifier of the environment
     * @param ownerId       identifier of the user that must own it
     * @return the managed entity
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional(readOnly = true)
    public EnvironmentEntity getRequiredEnvironmentEntity(
            final UUID environmentId,
            final UUID ownerId) {

        return environmentRepository.findByIdAndOwnerId(environmentId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, environmentId));
    }
}
