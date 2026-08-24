package com.kovospace.newtablinks.user.services;

import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.user.dtos.UserDto;
import com.kovospace.newtablinks.user.dtos.UserProfileUpdateRequestDto;
import com.kovospace.newtablinks.user.mappers.UserMapper;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business operations on the signed-in user's own account.
 *
 * <p>Accounts are created by registration and by provider sign-in, not here; this service reads
 * and edits accounts that already exist.</p>
 *
 * @since 0.0.1
 */
@Service
public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);
    private static final String RESOURCE_NAME = "User";

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * Creates the service.
     *
     * @param userRepository persistence access for users
     * @param userMapper     converter to the client facing shape
     */
    public UserService(final UserRepository userRepository, final UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    /**
     * Returns a single user.
     *
     * @param userId identifier of the user
     * @return the user
     * @throws ResourceNotFoundException when no user has that identifier
     */
    @Transactional(readOnly = true)
    public UserDto findUserById(final UUID userId) {
        return userMapper.toDto(getRequiredUserEntity(userId));
    }

    /**
     * Changes a user's display name.
     *
     * @param userId        identifier of the user to update
     * @param updateRequest the values to store
     * @return the updated user
     * @throws ResourceNotFoundException when no user has that identifier
     */
    @Transactional
    public UserDto updateProfile(
            final UUID userId,
            final UserProfileUpdateRequestDto updateRequest) {

        final UserEntity existingUser = getRequiredUserEntity(userId);
        existingUser.setDisplayName(updateRequest.displayName());
        return userMapper.toDto(existingUser);
    }

    /**
     * Deletes a user.
     *
     * @param userId identifier of the user to delete
     * @throws ResourceNotFoundException when no user has that identifier
     */
    @Transactional
    public void deleteUser(final UUID userId) {
        userRepository.delete(getRequiredUserEntity(userId));
        LOGGER.info("Account {} deleted", userId);
    }

    /**
     * Loads a user entity for another service in this application.
     *
     * <p>Entities do not leave the service layer towards a client; this method exists so that
     * services owning other parts of the model can resolve a user without reaching into this
     * module's repository.</p>
     *
     * @param userId identifier of the user
     * @return the managed entity
     * @throws ResourceNotFoundException when no user has that identifier
     */
    @Transactional(readOnly = true)
    public UserEntity getRequiredUserEntity(final UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, userId));
    }
}
