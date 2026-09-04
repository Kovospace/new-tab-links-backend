package com.kovospace.newtablinks.environment.services;

import com.kovospace.newtablinks.common.utils.ClientAssignedIdentifierPolicy;
import com.kovospace.newtablinks.environment.dtos.EnvironmentSynchronizedValuesDto;
import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import com.kovospace.newtablinks.environment.repositories.EnvironmentRepository;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies pushed synchronization operations to environments.
 *
 * <p>Separate from {@link EnvironmentService} for the reason given on
 * {@link com.kovospace.newtablinks.profile.services.ProfileSynchronizationService}, and nothing
 * here throws when a row is absent, for the reason given there too.</p>
 *
 * <p>The owner is never passed in: it is read from the parent profile, which the caller resolved
 * with the access token's identifier. An environment therefore cannot be filed under one user's
 * profile while belonging to another, whatever the pushed operation claims.</p>
 *
 * @since 0.0.6
 */
@Service
public class EnvironmentSynchronizationService {

    private final EnvironmentRepository environmentRepository;
    private final UserDataChangePublisher userDataChangePublisher;

    /**
     * Creates the service.
     *
     * @param environmentRepository   persistence access for environments
     * @param userDataChangePublisher announces changes to the user's other browsers
     */
    public EnvironmentSynchronizationService(
            final EnvironmentRepository environmentRepository,
            final UserDataChangePublisher userDataChangePublisher) {

        this.environmentRepository = environmentRepository;
        this.userDataChangePublisher = userDataChangePublisher;
    }

    /**
     * Looks an environment up without throwing when it is absent or somebody else's.
     *
     * @param environmentId identifier of the environment
     * @param ownerId       identifier of the user that must own it
     * @return the managed entity, or an empty optional when it does not exist or is not theirs
     */
    @Transactional(readOnly = true)
    public Optional<EnvironmentEntity> findEnvironmentEntityForOwner(
            final UUID environmentId,
            final UUID ownerId) {

        return environmentRepository.findByIdAndOwnerId(environmentId, ownerId);
    }

    /**
     * Stores an environment the client pushed, updating the owner's existing row or inserting one.
     *
     * @param requestedEnvironmentId identifier the client wants the environment stored under
     * @param parentProfile          profile the environment is filed under, already resolved
     *                               against the caller's own identifier
     * @param values                 the fields to store, all of which are replaced
     * @return the stored entity, whose identifier differs from the requested one exactly when the
     *         requested one was already taken
     */
    @Transactional
    public EnvironmentEntity upsertEnvironmentFromPushedOperation(
            final UUID requestedEnvironmentId,
            final ProfileEntity parentProfile,
            final EnvironmentSynchronizedValuesDto values) {

        final UUID ownerId = parentProfile.getOwner().getId();
        final Optional<EnvironmentEntity> existingEnvironment =
                environmentRepository.findByIdAndOwnerId(requestedEnvironmentId, ownerId);

        userDataChangePublisher.publishChangeFor(ownerId);

        if (existingEnvironment.isPresent()) {
            return applyValues(existingEnvironment.get(), parentProfile, values);
        }
        return insertEnvironment(requestedEnvironmentId, parentProfile, values);
    }

    /**
     * Deletes an environment if the owner still has one under that identifier.
     *
     * <p>A delete of something already gone is not an error; an offline queue is replayed at
     * least once.</p>
     *
     * @param environmentId identifier of the environment to delete
     * @param ownerId       identifier of the user that must own it
     * @return {@code true} when a row was deleted, {@code false} when there was nothing to delete
     */
    @Transactional
    public boolean deleteEnvironmentFromPushedOperationIfPresent(
            final UUID environmentId,
            final UUID ownerId) {

        final Optional<EnvironmentEntity> existingEnvironment =
                environmentRepository.findByIdAndOwnerId(environmentId, ownerId);

        if (existingEnvironment.isEmpty()) {
            return false;
        }
        environmentRepository.delete(existingEnvironment.get());
        userDataChangePublisher.publishChangeFor(ownerId);
        return true;
    }

    /**
     * Inserts an environment, under the client's identifier when that identifier is still free.
     *
     * @param requestedEnvironmentId identifier the client asked for
     * @param parentProfile          profile the environment is filed under
     * @param values                 the fields to store
     * @return the inserted entity
     */
    private EnvironmentEntity insertEnvironment(
            final UUID requestedEnvironmentId,
            final ProfileEntity parentProfile,
            final EnvironmentSynchronizedValuesDto values) {

        final EnvironmentEntity newEnvironment = new EnvironmentEntity(
                parentProfile, values.name(), values.description(), values.position());

        newEnvironment.setId(ClientAssignedIdentifierPolicy.chooseIdentifierForInsert(
                requestedEnvironmentId, environmentRepository::existsById));

        return environmentRepository.save(newEnvironment);
    }

    /**
     * Overwrites every synchronized field of an existing environment, including its profile.
     *
     * @param existingEnvironment the managed entity to update
     * @param parentProfile       profile the environment is filed under
     * @param values              the fields to store
     * @return the same entity, updated
     */
    private EnvironmentEntity applyValues(
            final EnvironmentEntity existingEnvironment,
            final ProfileEntity parentProfile,
            final EnvironmentSynchronizedValuesDto values) {

        existingEnvironment.setProfile(parentProfile);
        existingEnvironment.setName(values.name());
        existingEnvironment.setDescription(values.description());
        existingEnvironment.setPosition(values.position());
        return existingEnvironment;
    }
}
