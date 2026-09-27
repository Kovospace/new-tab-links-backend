package com.kovospace.newtablinks.common.services;

import com.kovospace.newtablinks.auth.repositories.EmailedTokenRepository;
import com.kovospace.newtablinks.auth.repositories.RefreshTokenRepository;
import com.kovospace.newtablinks.auth.repositories.SingleUseCodeRepository;
import com.kovospace.newtablinks.entitlement.repositories.EntitlementRepository;
import com.kovospace.newtablinks.environment.repositories.EnvironmentRepository;
import com.kovospace.newtablinks.profile.repositories.ProfileRepository;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserDeviceRepository;
import com.kovospace.newtablinks.user.repositories.UserIdentityRepository;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes an account and every row anywhere in the application that names it.
 *
 * <p>Eight tables carry a non-null foreign key to a user - profiles, environments, refresh
 * tokens, devices, emailed tokens, single use codes, linked identities and the pro entitlement -
 * and none of them is
 * mapped from the user's side. Deleting the user row on its own therefore relied entirely on the
 * migrated schema's {@code ON DELETE CASCADE}, for the same reason and with the same risk set out
 * on {@link HierarchyDeletionService}: the rule lives in another repository, {@code validate}
 * does not check it, and a schema Hibernate generates itself does not have it, so the delete
 * fails outright on a developer's machine.</p>
 *
 * <p>Two orderings matter and neither is obvious from the user table alone. A refresh token
 * names the device it was issued to as well as the user, so tokens go before devices. And an
 * environment names a profile as well as an owner, so environments are cleared through
 * {@link HierarchyDeletionService} rather than swept away by owner - deleting one directly would
 * leave its groups and links behind, and they hold it in place in turn.</p>
 *
 * <p>It lives beside {@link HierarchyDeletionService} rather than in the user module because it
 * has to reach into auth, and the user module is what auth is built on: putting it there would
 * point that dependency back at itself.</p>
 *
 * @since 0.0.7
 */
@Service
public class AccountDeletionService {

    private final HierarchyDeletionService hierarchyDeletionService;
    private final ProfileRepository profileRepository;
    private final EnvironmentRepository environmentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final EmailedTokenRepository emailedTokenRepository;
    private final SingleUseCodeRepository singleUseCodeRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final EntitlementRepository entitlementRepository;
    private final UserRepository userRepository;

    /**
     * Creates the service.
     *
     * @param hierarchyDeletionService removes a profile together with everything beneath it
     * @param profileRepository        persistence access for profiles
     * @param environmentRepository    persistence access for environments
     * @param refreshTokenRepository   persistence access for refresh tokens
     * @param userDeviceRepository     persistence access for the user's devices
     * @param emailedTokenRepository   persistence access for activation and reset tokens
     * @param singleUseCodeRepository  persistence access for single use codes
     * @param userIdentityRepository   persistence access for linked external identities
     * @param entitlementRepository    persistence access for the pro entitlement
     * @param userRepository           persistence access for users
     */
    public AccountDeletionService(
            final HierarchyDeletionService hierarchyDeletionService,
            final ProfileRepository profileRepository,
            final EnvironmentRepository environmentRepository,
            final RefreshTokenRepository refreshTokenRepository,
            final UserDeviceRepository userDeviceRepository,
            final EmailedTokenRepository emailedTokenRepository,
            final SingleUseCodeRepository singleUseCodeRepository,
            final UserIdentityRepository userIdentityRepository,
            final EntitlementRepository entitlementRepository,
            final UserRepository userRepository) {

        this.hierarchyDeletionService = hierarchyDeletionService;
        this.profileRepository = profileRepository;
        this.environmentRepository = environmentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.emailedTokenRepository = emailedTokenRepository;
        this.singleUseCodeRepository = singleUseCodeRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.entitlementRepository = entitlementRepository;
        this.userRepository = userRepository;
    }

    /**
     * Deletes an account and everything it owns.
     *
     * <p>The caller is responsible for having established that this is the account making the
     * request; nothing here re-checks, because the only entry point resolves the user from the
     * access token.</p>
     *
     * @param user the account to remove
     */
    @Transactional
    public void deleteAccountWithEverythingItOwns(final UserEntity user) {

        final UUID userId = user.getId();

        deleteEverythingTheUserHasStored(userId);
        deleteEverythingTheUserSignsInWith(userId);
        // Whether the account MAY be deleted while a subscription is still billing is a
        // separate, later decision (TODOS: "account deletion is blocked while billing is live").
        // This only keeps the delete from failing on the foreign key.
        entitlementRepository.findByOwnerId(userId).ifPresent(entitlementRepository::delete);

        userRepository.delete(user);
    }

    /**
     * Clears the link hierarchy, profile by profile.
     *
     * <p>The sweep for environments afterwards is a safety net, not a normal case: an
     * environment cannot exist without a profile, so the first loop should have taken them all.
     * One left behind by a half-finished migration would otherwise block the delete with a
     * message naming a table the user has never heard of.</p>
     *
     * @param userId identifier of the account being removed
     */
    private void deleteEverythingTheUserHasStored(final UUID userId) {

        profileRepository.findAllByOwnerIdOrderByPositionAsc(userId)
                .forEach(hierarchyDeletionService::deleteProfileWithDescendants);

        environmentRepository.findAllByOwnerIdOrderByPositionAsc(userId)
                .forEach(hierarchyDeletionService::deleteEnvironmentWithDescendants);
    }

    /**
     * Clears the sessions, devices, tokens and identities the account was reachable through.
     *
     * <p>Refresh tokens first: each one names the device it was issued to, so a device cannot go
     * while a token of its own still points at it.</p>
     *
     * @param userId identifier of the account being removed
     */
    private void deleteEverythingTheUserSignsInWith(final UUID userId) {

        refreshTokenRepository.deleteAll(refreshTokenRepository.findAllByUserId(userId));
        userDeviceRepository.deleteAll(
                userDeviceRepository.findAllByUserIdOrderByLastUsedAtDesc(userId));

        emailedTokenRepository.deleteAll(emailedTokenRepository.findAllByUserId(userId));
        singleUseCodeRepository.deleteAll(singleUseCodeRepository.findAllByUserId(userId));
        userIdentityRepository.deleteAll(userIdentityRepository.findAllByUserId(userId));
    }
}
