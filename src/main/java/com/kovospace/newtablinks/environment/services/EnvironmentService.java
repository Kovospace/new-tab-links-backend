package com.kovospace.newtablinks.environment.services;

import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.utils.DisplayPositionCalculator;
import com.kovospace.newtablinks.environment.dtos.EnvironmentDto;
import com.kovospace.newtablinks.environment.dtos.EnvironmentSaveRequestDto;
import com.kovospace.newtablinks.environment.mappers.EnvironmentMapper;
import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import com.kovospace.newtablinks.environment.repositories.EnvironmentRepository;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.services.UserService;
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
    private final UserService userService;

    /**
     * Creates the service.
     *
     * @param environmentRepository persistence access for environments
     * @param environmentMapper     converter to the client facing shape
     * @param userService           resolves the owning user
     */
    public EnvironmentService(
            final EnvironmentRepository environmentRepository,
            final EnvironmentMapper environmentMapper,
            final UserService userService) {

        this.environmentRepository = environmentRepository;
        this.environmentMapper = environmentMapper;
        this.userService = userService;
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
     * Returns a single environment.
     *
     * @param environmentId identifier of the environment
     * @return the environment
     * @throws ResourceNotFoundException when no environment has that identifier
     */
    @Transactional(readOnly = true)
    public EnvironmentDto findEnvironmentById(final UUID environmentId) {
        return environmentMapper.toDto(getRequiredEnvironmentEntity(environmentId));
    }

    /**
     * Creates an environment and appends it after the owner's existing ones.
     *
     * @param saveRequest the environment to create
     * @return the created environment, including its assigned identifier and position
     * @throws ResourceNotFoundException when the owning user does not exist
     */
    @Transactional
    public EnvironmentDto createEnvironment(final EnvironmentSaveRequestDto saveRequest) {
        final UserEntity owner = userService.getRequiredUserEntity(saveRequest.ownerId());
        final int position = DisplayPositionCalculator.calculatePositionForAppendedItem(
                environmentRepository.findHighestPositionByOwnerId(owner.getId()));

        final EnvironmentEntity newEnvironment =
                new EnvironmentEntity(owner, saveRequest.name(), position);

        return environmentMapper.toDto(environmentRepository.save(newEnvironment));
    }

    /**
     * Renames an existing environment.
     *
     * <p>The owner is not reassigned: moving an environment between users is not a rename and
     * would need its own operation.</p>
     *
     * @param environmentId identifier of the environment to update
     * @param saveRequest   the values to store
     * @return the updated environment
     * @throws ResourceNotFoundException when no environment has that identifier
     */
    @Transactional
    public EnvironmentDto updateEnvironment(
            final UUID environmentId,
            final EnvironmentSaveRequestDto saveRequest) {

        final EnvironmentEntity existingEnvironment = getRequiredEnvironmentEntity(environmentId);
        existingEnvironment.setName(saveRequest.name());
        return environmentMapper.toDto(existingEnvironment);
    }

    /**
     * Deletes an environment.
     *
     * @param environmentId identifier of the environment to delete
     * @throws ResourceNotFoundException when no environment has that identifier
     */
    @Transactional
    public void deleteEnvironment(final UUID environmentId) {
        environmentRepository.delete(getRequiredEnvironmentEntity(environmentId));
    }

    /**
     * Loads an environment entity for another service in this application.
     *
     * @param environmentId identifier of the environment
     * @return the managed entity
     * @throws ResourceNotFoundException when no environment has that identifier
     */
    @Transactional(readOnly = true)
    public EnvironmentEntity getRequiredEnvironmentEntity(final UUID environmentId) {
        return environmentRepository.findById(environmentId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, environmentId));
    }
}
