package com.kovospace.newtablinks.user.services;

import com.kovospace.newtablinks.common.exceptions.PaidEntitlementRevocationException;
import com.kovospace.newtablinks.common.exceptions.RegistrationConflictException;
import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.entitlement.models.OperatorProDecisionOutcome;
import com.kovospace.newtablinks.entitlement.models.PremiumGrantTerm;
import com.kovospace.newtablinks.entitlement.models.ProStanding;
import com.kovospace.newtablinks.entitlement.services.EntitlementGrantService;
import com.kovospace.newtablinks.entitlement.services.EntitlementStandingService;
import com.kovospace.newtablinks.user.dtos.AdminUserCreateRequestDto;
import com.kovospace.newtablinks.user.dtos.AdminUserDto;
import com.kovospace.newtablinks.user.dtos.AdminUserPageDto;
import com.kovospace.newtablinks.user.dtos.AdminUserUpdateRequestDto;
import com.kovospace.newtablinks.user.mappers.AdminUserMapper;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the operator can do to an account.
 *
 * <p>Separate from {@link UserService}, which serves a signed-in user acting on their own
 * account, because these are different powers over the same rows and must not be reachable
 * through one another. Everything here acts on an account named by identifier and is authorised
 * only by an admin token; nothing here is ownership-scoped, which is the whole point and also
 * exactly why it lives behind its own authority.</p>
 *
 * <p>It sits in the user module rather than in the admin one so that account persistence stays
 * inside the module that owns it - the admin module owns the operator's identity, not the data
 * they repair.</p>
 *
 * <p>Pro given by the operator goes through {@link EntitlementGrantService} in the same
 * transaction as the rest of the change, so a refused revocation leaves the account untouched.</p>
 *
 * <p><strong>Every method here writes a log line naming the account.</strong> These operations
 * have no owner to notice them and no undo, so the log is the only record that they happened.</p>
 *
 * @since 0.0.6
 */
@Service
public class UserAdministrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserAdministrationService.class);

    /** Name used when reporting that an account could not be found. */
    private static final String RESOURCE_NAME = "User";

    /** Newest accounts first: the one somebody is looking for is usually a recent one. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    /** Names the operator action creating an account, in the premium log line. */
    private static final String CREATE_ACTION = "create";

    /** Names the operator action updating an account, in the premium log line. */
    private static final String UPDATE_ACTION = "update";

    private final UserRepository userRepository;
    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final EntitlementStandingService entitlementStandingService;
    private final EntitlementGrantService entitlementGrantService;

    /**
     * Creates the service.
     *
     * @param userRepository  persistence for accounts
     * @param adminUserMapper renders an account in the operator's shape
     * @param passwordEncoder hashes a password the operator sets
     * @param entitlementStandingService tells whether an account is pro, and through what
     * @param entitlementGrantService    gives and takes back pro the operator grants
     */
    public UserAdministrationService(
            final UserRepository userRepository,
            final AdminUserMapper adminUserMapper,
            final PasswordEncoder passwordEncoder,
            final EntitlementStandingService entitlementStandingService,
            final EntitlementGrantService entitlementGrantService) {

        this.userRepository = userRepository;
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.entitlementStandingService = entitlementStandingService;
        this.entitlementGrantService = entitlementGrantService;
    }

    /**
     * Lists accounts, newest first, optionally narrowed by a search.
     *
     * <p>The pro standing of the whole page is read in one query, not one per account.</p>
     *
     * @param searchText text to match against username, email and display name; blank matches all
     * @param page       zero-based page index
     * @param size       how many accounts to return
     * @return the requested page
     */
    @Transactional(readOnly = true)
    public AdminUserPageDto listAccounts(final String searchText, final int page, final int size) {
        final Page<UserEntity> matchingAccounts = userRepository.searchAccounts(
                searchText == null ? "" : searchText.trim(),
                PageRequest.of(page, size, NEWEST_FIRST));

        final List<UserEntity> accountsOnPage = matchingAccounts.getContent();
        final Map<UUID, ProStanding> proStandingByOwner =
                entitlementStandingService.describeProStandingNowOfEach(
                        accountsOnPage.stream().map(UserEntity::getId).toList());

        return new AdminUserPageDto(
                adminUserMapper.toDtoList(accountsOnPage, proStandingByOwner),
                matchingAccounts.getNumber(),
                matchingAccounts.getSize(),
                matchingAccounts.getTotalElements(),
                matchingAccounts.getTotalPages());
    }

    /**
     * Reads one account.
     *
     * @param userId identifier of the account
     * @return the account
     * @throws ResourceNotFoundException when no account has that identifier
     */
    @Transactional(readOnly = true)
    public AdminUserDto findAccount(final UUID userId) {
        return toAdminUserDto(getRequiredAccount(userId));
    }

    /**
     * Creates an account without going through registration.
     *
     * <p>When the request asks for premium, the account is given an operator grant of the
     * requested term - lifetime when none is named - in the same transaction.</p>
     *
     * @param createRequest the account to create
     * @return the created account
     * @throws RegistrationConflictException when the username or email is already taken
     */
    @Transactional
    public AdminUserDto createAccount(final AdminUserCreateRequestDto createRequest) {
        if (userRepository.existsByUsername(createRequest.username())) {
            throw new RegistrationConflictException("That username is already taken");
        }
        if (userRepository.existsByEmail(createRequest.email())) {
            // Unlike registration, this says so plainly. Registration hides it to keep itself
            // from being an address-discovery service; the operator is already trusted with
            // every address in the database.
            throw new RegistrationConflictException("That email address is already registered");
        }

        final UserEntity account = new UserEntity(
                createRequest.username(),
                createRequest.email(),
                createRequest.password() == null || createRequest.password().isBlank()
                        ? null
                        : passwordEncoder.encode(createRequest.password()),
                createRequest.displayName(),
                createRequest.status());

        final UserEntity createdAccount = userRepository.save(account);
        LOGGER.info("Operator created account {} ({}) with status {}",
                createdAccount.getId(), createdAccount.getUsername(), createdAccount.getStatus());

        if (createRequest.premium()) {
            applyPremiumDecision(
                    createdAccount, true, createRequest.premiumGrantTerm(), CREATE_ACTION);
        }
        return toAdminUserDto(createdAccount);
    }

    /**
     * Replaces the fields an operator may change, and grants or revokes operator-given pro.
     *
     * <p>A {@code premium} of {@code null} leaves pro as it is, and so does {@code true} without a
     * {@code premiumGrantTerm} on an account already pro; a term re-applies an operator grant
     * from now. The premium decision is taken
     * before anything else changes, and in the same transaction, so a refusal changes nothing.</p>
     *
     * @param userId        identifier of the account
     * @param updateRequest the new values
     * @return the updated account
     * @throws ResourceNotFoundException          when no account has that identifier
     * @throws RegistrationConflictException      when the email address belongs to another
     *                                            account
     * @throws PaidEntitlementRevocationException when asked to revoke pro that was paid for
     */
    @Transactional
    public AdminUserDto updateAccount(
            final UUID userId,
            final AdminUserUpdateRequestDto updateRequest) {

        final UserEntity account = getRequiredAccount(userId);
        final String previousEmail = account.getEmail();

        if (!previousEmail.equalsIgnoreCase(updateRequest.email())
                && userRepository.existsByEmail(updateRequest.email())) {

            throw new RegistrationConflictException("That email address is already registered");
        }
        if (updateRequest.premium() != null) {
            applyPremiumDecision(account, updateRequest.premium(),
                    updateRequest.premiumGrantTerm(), UPDATE_ACTION);
        }

        account.setEmail(updateRequest.email());
        account.setDisplayName(updateRequest.displayName());
        account.setStatus(updateRequest.status());

        if (!previousEmail.equals(updateRequest.email())) {
            // Logged on its own because it is the one change here that moves an identity, and
            // the one users are not allowed to make themselves.
            LOGGER.warn("Operator changed the email address of account {}", userId);
        }
        LOGGER.info("Operator updated account {} to status {}", userId, updateRequest.status());

        return toAdminUserDto(account);
    }

    /**
     * Clears the failed sign-in counter that locks an account out.
     *
     * @param userId identifier of the account
     * @return the unlocked account
     * @throws ResourceNotFoundException when no account has that identifier
     */
    @Transactional
    public AdminUserDto unlockAccount(final UUID userId) {
        final UserEntity account = getRequiredAccount(userId);
        account.resetFailedLoginAttempts();

        LOGGER.info("Operator cleared the failed sign-in counter of account {}", userId);
        return toAdminUserDto(account);
    }

    /**
     * Sets an account's password, or takes it away.
     *
     * @param userId      identifier of the account
     * @param newPassword the password to set, or {@code null} to leave the account without one
     * @return the account
     * @throws ResourceNotFoundException when no account has that identifier
     */
    @Transactional
    public AdminUserDto setPassword(final UUID userId, final String newPassword) {
        final UserEntity account = getRequiredAccount(userId);
        account.setPasswordHash(
                newPassword == null || newPassword.isBlank()
                        ? null
                        : passwordEncoder.encode(newPassword));

        LOGGER.warn("Operator set the password of account {}", userId);
        return toAdminUserDto(account);
    }

    /**
     * Deletes an account and everything it owns.
     *
     * <p><strong>Irreversible, and wider than it looks.</strong> Every environment, group,
     * subgroup and link belonging to the account goes with it, by foreign keys declared
     * {@code ON DELETE CASCADE}; so do its devices, sessions and pending tokens. There is no
     * soft delete and nothing to undo it with - {@code DISABLED} is the reversible option, and
     * usually the one that was meant.</p>
     *
     * @param userId identifier of the account
     * @throws ResourceNotFoundException when no account has that identifier
     */
    @Transactional
    public void deleteAccount(final UUID userId) {
        final UserEntity account = getRequiredAccount(userId);
        final String username = account.getUsername();

        userRepository.delete(account);
        LOGGER.warn("Operator deleted account {} ({}) and everything it owned", userId, username);
    }

    /**
     * Grants or revokes operator-given pro, and logs it when anything changed.
     *
     * @param account          the account, already persisted
     * @param shouldBePremium  {@code true} to grant, {@code false} to revoke
     * @param grantTerm        how long a grant lasts, or {@code null}; see
     *                         {@link EntitlementGrantService#grantPro}. Ignored when revoking
     * @param operatorAction   the admin action this is part of, for the log line
     * @throws PaidEntitlementRevocationException when asked to revoke pro that was paid for
     */
    private void applyPremiumDecision(
            final UserEntity account,
            final boolean shouldBePremium,
            final PremiumGrantTerm grantTerm,
            final String operatorAction) {

        final OperatorProDecisionOutcome outcome = shouldBePremium
                ? entitlementGrantService.grantPro(account, grantTerm)
                : entitlementGrantService.revokeGrantedPro(account);

        if (outcome.changedStanding()) {
            LOGGER.info("Operator {} of account {}: premium {}",
                    operatorAction, account.getId(), outcome);
        } else {
            LOGGER.debug("Operator {} of account {}: premium unchanged ({})",
                    operatorAction, account.getId(), outcome);
        }
    }

    /**
     * Renders an account in the operator's shape, with its current pro standing.
     *
     * @param account the account
     * @return the account as the operator sees it
     */
    private AdminUserDto toAdminUserDto(final UserEntity account) {
        return adminUserMapper.toDto(
                account, entitlementStandingService.describeProStandingNow(account.getId()));
    }

    /**
     * Loads an account or reports that there is none.
     *
     * @param userId identifier of the account
     * @return the managed entity
     * @throws ResourceNotFoundException when no account has that identifier
     */
    private UserEntity getRequiredAccount(final UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, userId));
    }
}
