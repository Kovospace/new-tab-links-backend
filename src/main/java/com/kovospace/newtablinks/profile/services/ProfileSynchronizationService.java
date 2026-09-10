package com.kovospace.newtablinks.profile.services;

import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.services.HierarchyDeletionService;
import com.kovospace.newtablinks.common.utils.ClientAssignedIdentifierPolicy;
import com.kovospace.newtablinks.profile.dtos.ProfileSynchronizedValuesDto;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.repositories.ProfileRepository;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import com.kovospace.newtablinks.user.services.UserService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies pushed synchronization operations to profiles.
 *
 * <p>Separate from {@link ProfileService} because it answers a different question. The
 * interactive endpoints serve a person editing one thing at a time and deliberately leave the
 * display position alone; a push replays a device's whole offline history, names the identifier
 * to store under, and does set the position, because reordering is one of the changes it is
 * replaying.</p>
 *
 * <p>Nothing here throws when a row is absent. A rejected operation must leave the surrounding
 * transaction usable, and an exception crossing a {@code @Transactional} boundary marks that
 * transaction rollback-only however the caller handles it - which would turn one unresolvable
 * parent into a failed push of everything.</p>
 *
 * @since 0.0.6
 */
@Service
public class ProfileSynchronizationService {

    private final ProfileRepository profileRepository;
    private final UserService userService;
    private final UserDataChangePublisher userDataChangePublisher;
    private final HierarchyDeletionService hierarchyDeletionService;

    /**
     * Creates the service.
     *
     * @param profileRepository        persistence access for profiles
     * @param userService              resolves the owning user
     * @param userDataChangePublisher  announces changes to the user's other browsers
     * @param hierarchyDeletionService removes a record together with everything beneath it
     */
    public ProfileSynchronizationService(
            final ProfileRepository profileRepository,
            final UserService userService,
            final UserDataChangePublisher userDataChangePublisher,
            final HierarchyDeletionService hierarchyDeletionService) {

        this.profileRepository = profileRepository;
        this.userService = userService;
        this.userDataChangePublisher = userDataChangePublisher;
        this.hierarchyDeletionService = hierarchyDeletionService;
    }

    /**
     * Looks a profile up without throwing when it is absent or somebody else's.
     *
     * @param profileId identifier of the profile
     * @param ownerId   identifier of the user that must own it
     * @return the managed entity, or an empty optional when it does not exist or is not theirs
     */
    @Transactional(readOnly = true)
    public Optional<ProfileEntity> findProfileEntityForOwner(
            final UUID profileId,
            final UUID ownerId) {

        return profileRepository.findByIdAndOwnerId(profileId, ownerId);
    }

    /**
     * Stores a profile the client pushed, updating the owner's existing row or inserting a new one.
     *
     * @param requestedProfileId identifier the client wants the profile stored under
     * @param ownerId            identifier of the user the profile belongs to
     * @param values             the fields to store, all of which are replaced
     * @return the stored entity, whose identifier differs from the requested one exactly when the
     *         requested one was already taken and the client has to be told about a remapping
     * @throws ResourceNotFoundException when the owning user does not exist, which would mean an
     *                                   access token outliving its account
     */
    @Transactional
    public ProfileEntity upsertProfileFromPushedOperation(
            final UUID requestedProfileId,
            final UUID ownerId,
            final ProfileSynchronizedValuesDto values) {

        final Optional<ProfileEntity> existingProfile =
                profileRepository.findByIdAndOwnerId(requestedProfileId, ownerId);

        userDataChangePublisher.publishChangeFor(ownerId);

        if (existingProfile.isPresent()) {
            return applyValues(existingProfile.get(), values);
        }
        return insertProfile(requestedProfileId, ownerId, values);
    }

    /**
     * Deletes a profile, and everything inside it, if the owner still has one under that
     * identifier.
     *
     * <p>A delete of something already gone is not an error: an offline queue is replayed at
     * least once, and the second replay of a delete has nothing left to remove.</p>
     *
     * @param profileId identifier of the profile to delete
     * @param ownerId   identifier of the user that must own it
     * @return {@code true} when a row was deleted, {@code false} when there was nothing to delete
     */
    @Transactional
    public boolean deleteProfileFromPushedOperationIfPresent(
            final UUID profileId,
            final UUID ownerId) {

        final Optional<ProfileEntity> existingProfile =
                profileRepository.findByIdAndOwnerId(profileId, ownerId);

        if (existingProfile.isEmpty()) {
            return false;
        }
        hierarchyDeletionService.deleteProfileWithDescendants(existingProfile.get());
        userDataChangePublisher.publishChangeFor(ownerId);
        return true;
    }

    /**
     * Inserts a profile, under the client's identifier when that identifier is still free.
     *
     * @param requestedProfileId identifier the client asked for
     * @param ownerId            identifier of the user the profile belongs to
     * @param values             the fields to store
     * @return the inserted entity
     */
    private ProfileEntity insertProfile(
            final UUID requestedProfileId,
            final UUID ownerId,
            final ProfileSynchronizedValuesDto values) {

        final ProfileEntity newProfile = new ProfileEntity(
                userService.getRequiredUserEntity(ownerId), values.name(), values.position());
        newProfile.setEnableDragAndDrop(values.enableDragAndDrop());

        newProfile.setId(ClientAssignedIdentifierPolicy.chooseIdentifierForInsert(
                requestedProfileId, profileRepository::existsById));

        return profileRepository.save(newProfile);
    }

    /**
     * Overwrites every synchronized field of an existing profile.
     *
     * @param existingProfile the managed entity to update
     * @param values          the fields to store
     * @return the same entity, updated
     */
    private ProfileEntity applyValues(
            final ProfileEntity existingProfile,
            final ProfileSynchronizedValuesDto values) {

        existingProfile.setName(values.name());
        existingProfile.setEnableDragAndDrop(values.enableDragAndDrop());
        existingProfile.setPosition(values.position());
        return existingProfile;
    }
}
