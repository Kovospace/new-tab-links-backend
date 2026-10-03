package com.kovospace.newtablinks.profile.services;

import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.services.HierarchyDeletionService;
import com.kovospace.newtablinks.common.utils.DisplayPositionCalculator;
import com.kovospace.newtablinks.profile.dtos.ProfileDto;
import com.kovospace.newtablinks.profile.dtos.ProfileSaveRequestDto;
import com.kovospace.newtablinks.profile.mappers.ProfileMapper;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.repositories.ProfileRepository;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.services.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business operations on profiles.
 *
 * <p>Every method takes the identifier of the user the request is authenticated as, and every
 * lookup is scoped to it. A row belonging to somebody else is reported as missing rather than as
 * forbidden, so the API cannot be used to confirm that another user's identifier exists.</p>
 *
 * @since 0.0.6
 */
@Service
public class ProfileService {

    private static final String RESOURCE_NAME = "Profile";

    private final ProfileRepository profileRepository;
    private final ProfileMapper profileMapper;
    private final UserService userService;
    private final UserDataChangePublisher userDataChangePublisher;
    private final HierarchyDeletionService hierarchyDeletionService;

    /**
     * Creates the service.
     *
     * @param profileRepository        persistence access for profiles
     * @param profileMapper            converter to the client facing shape
     * @param userService              resolves the owning user
     * @param userDataChangePublisher  announces changes to the user's other browsers
     * @param hierarchyDeletionService removes a record together with everything beneath it
     */
    public ProfileService(
            final ProfileRepository profileRepository,
            final ProfileMapper profileMapper,
            final UserService userService,
            final UserDataChangePublisher userDataChangePublisher,
            final HierarchyDeletionService hierarchyDeletionService) {

        this.profileRepository = profileRepository;
        this.profileMapper = profileMapper;
        this.userService = userService;
        this.userDataChangePublisher = userDataChangePublisher;
        this.hierarchyDeletionService = hierarchyDeletionService;
    }

    /**
     * Lists a user's profiles in display order.
     *
     * @param ownerId identifier of the owning user
     * @return the owner's profiles, empty when there are none
     */
    @Transactional(readOnly = true)
    public List<ProfileDto> findProfilesByOwner(final UUID ownerId) {
        return profileMapper.toDtoList(
                profileRepository.findAllByOwnerIdOrderByPositionAsc(ownerId));
    }

    /**
     * Returns a single profile belonging to the given owner.
     *
     * @param profileId identifier of the profile
     * @param ownerId   identifier of the user that must own it
     * @return the profile
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional(readOnly = true)
    public ProfileDto findProfileById(final UUID profileId, final UUID ownerId) {
        return profileMapper.toDto(getRequiredProfileEntity(profileId, ownerId));
    }

    /**
     * Creates a profile and appends it after the owner's existing ones.
     *
     * @param saveRequest the profile to create
     * @param ownerId     identifier of the user it is created for, taken from the access token
     * @return the created profile, including its assigned identifier and position
     * @throws ResourceNotFoundException when the owning user does not exist
     */
    @Transactional
    public ProfileDto createProfile(final ProfileSaveRequestDto saveRequest, final UUID ownerId) {
        final UserEntity owner = userService.getRequiredUserEntity(ownerId);
        final int position = DisplayPositionCalculator.calculatePositionForAppendedItem(
                profileRepository.findHighestPositionByOwnerId(owner.getId()));

        final ProfileEntity newProfile = new ProfileEntity(owner, saveRequest.name(), position);
        newProfile.setEnableDragAndDrop(saveRequest.enableDragAndDrop());
        newProfile.setHideTips(saveRequest.hideTips());
        newProfile.setDismissedTips(saveRequest.dismissedTips());

        userDataChangePublisher.publishChangeFor(ownerId);
        return profileMapper.toDto(profileRepository.save(newProfile));
    }

    /**
     * Updates the name and the settings of an existing profile.
     *
     * <p>The position is left untouched, as it is on every other interactive update in this
     * application; synchronization is what moves things.</p>
     *
     * @param profileId   identifier of the profile to update
     * @param saveRequest the values to store
     * @param ownerId     identifier of the user that must own it
     * @return the updated profile
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional
    public ProfileDto updateProfile(
            final UUID profileId,
            final ProfileSaveRequestDto saveRequest,
            final UUID ownerId) {

        final ProfileEntity existingProfile = getRequiredProfileEntity(profileId, ownerId);
        existingProfile.setName(saveRequest.name());
        existingProfile.setEnableDragAndDrop(saveRequest.enableDragAndDrop());
        existingProfile.setHideTips(saveRequest.hideTips());
        existingProfile.setDismissedTips(saveRequest.dismissedTips());
        userDataChangePublisher.publishChangeFor(ownerId);
        return profileMapper.toDto(existingProfile);
    }

    /**
     * Deletes a profile and everything inside it: its environments, their groups, and every
     * subgroup and link in those.
     *
     * @param profileId identifier of the profile to delete
     * @param ownerId   identifier of the user that must own it
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional
    public void deleteProfile(final UUID profileId, final UUID ownerId) {
        hierarchyDeletionService.deleteProfileWithDescendants(
                getRequiredProfileEntity(profileId, ownerId));
        userDataChangePublisher.publishChangeFor(ownerId);
    }

    /**
     * Loads a profile entity for another service in this application, enforcing ownership.
     *
     * <p>A row owned by somebody else is reported as missing rather than as forbidden, so that
     * the API never confirms that another user's identifier exists.</p>
     *
     * @param profileId identifier of the profile
     * @param ownerId   identifier of the user that must own it
     * @return the managed entity
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional(readOnly = true)
    public ProfileEntity getRequiredProfileEntity(final UUID profileId, final UUID ownerId) {
        return profileRepository.findByIdAndOwnerId(profileId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, profileId));
    }
}
