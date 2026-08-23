package com.kovospace.newtablinks.user.services;

import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.user.dtos.UserDto;
import com.kovospace.newtablinks.user.dtos.UserSaveRequestDto;
import com.kovospace.newtablinks.user.mappers.UserMapper;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business operations on users.
 *
 * @since 0.0.1
 */
@Service
public class UserService {

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
     * Lists every user.
     *
     * @return all users, empty when there are none
     */
    @Transactional(readOnly = true)
    public List<UserDto> findAllUsers() {
        return userMapper.toDtoList(userRepository.findAll());
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
     * Creates a user.
     *
     * @param saveRequest the user to create
     * @return the created user, including its assigned identifier
     */
    @Transactional
    public UserDto createUser(final UserSaveRequestDto saveRequest) {
        final UserEntity newUser = new UserEntity(saveRequest.email(), saveRequest.displayName());
        return userMapper.toDto(userRepository.save(newUser));
    }

    /**
     * Replaces the changeable fields of an existing user.
     *
     * @param userId      identifier of the user to update
     * @param saveRequest the values to store
     * @return the updated user
     * @throws ResourceNotFoundException when no user has that identifier
     */
    @Transactional
    public UserDto updateUser(final UUID userId, final UserSaveRequestDto saveRequest) {
        final UserEntity existingUser = getRequiredUserEntity(userId);
        existingUser.setEmail(saveRequest.email());
        existingUser.setDisplayName(saveRequest.displayName());
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
