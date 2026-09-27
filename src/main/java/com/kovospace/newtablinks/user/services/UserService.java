package com.kovospace.newtablinks.user.services;

import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.services.AccountDeletionService;
import com.kovospace.newtablinks.entitlement.services.EntitlementStandingService;
import com.kovospace.newtablinks.user.dtos.UserDto;
import com.kovospace.newtablinks.user.dtos.UserProfileUpdateRequestDto;
import com.kovospace.newtablinks.user.mappers.UserMapper;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.Optional;
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
    private final AccountDeletionService accountDeletionService;
    private final EntitlementStandingService entitlementStandingService;

    /**
     * Creates the service.
     *
     * @param userRepository         persistence access for users
     * @param userMapper             converter to the client facing shape
     * @param accountDeletionService     removes an account and everything it owns
     * @param entitlementStandingService tells whether the account is pro
     */
    public UserService(
            final UserRepository userRepository,
            final UserMapper userMapper,
            final AccountDeletionService accountDeletionService,
            final EntitlementStandingService entitlementStandingService) {

        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.accountDeletionService = accountDeletionService;
        this.entitlementStandingService = entitlementStandingService;
    }

    /**
     * Returns a single user, with whether the account is pro right now.
     *
     * @param userId identifier of the user
     * @return the user
     * @throws ResourceNotFoundException when no user has that identifier
     */
    @Transactional(readOnly = true)
    public UserDto findUserById(final UUID userId) {
        return toUserDtoWithCurrentStanding(getRequiredUserEntity(userId));
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
        return toUserDtoWithCurrentStanding(existingUser);
    }

    /**
     * Converts an account for a client, judging whether it is pro at this moment.
     *
     * @param userEntity the account
     * @return the account as returned to a client
     */
    private UserDto toUserDtoWithCurrentStanding(final UserEntity userEntity) {
        return userMapper.toDto(
                userEntity, entitlementStandingService.isAccountProNow(userEntity.getId()));
    }

    /**
     * Deletes a user, and with them their profiles, links, devices, sessions and linked
     * identities.
     *
     * <p>Nothing is kept back and nothing is anonymised: the account is gone, which is what the
     * endpoint offering this promises.</p>
     *
     * @param userId identifier of the user to delete
     * @throws ResourceNotFoundException when no user has that identifier
     */
    @Transactional
    public void deleteUser(final UUID userId) {
        accountDeletionService.deleteAccountWithEverythingItOwns(getRequiredUserEntity(userId));
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

    /**
     * Loads a user entity for another service, when it exists.
     *
     * <p>The counterpart of {@link #getRequiredUserEntity(UUID)} for callers to whom a missing
     * account is an expected answer rather than a failure - a payment webhook naming an account
     * that has since been deleted. It matters inside a caller's transaction: an exception thrown
     * through this transactional method would mark that transaction rollback-only even if the
     * caller caught it.</p>
     *
     * @param userId identifier of the user
     * @return the managed entity, or empty when no user has that identifier
     * @since 0.0.9
     */
    @Transactional(readOnly = true)
    public Optional<UserEntity> findUserEntity(final UUID userId) {
        return userRepository.findById(userId);
    }
}
