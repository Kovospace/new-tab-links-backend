package com.kovospace.newtablinks.common.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.auth.models.EmailedTokenEntity;
import com.kovospace.newtablinks.auth.models.RefreshTokenEntity;
import com.kovospace.newtablinks.auth.models.SingleUseCodeEntity;
import com.kovospace.newtablinks.auth.repositories.EmailedTokenRepository;
import com.kovospace.newtablinks.auth.repositories.RefreshTokenRepository;
import com.kovospace.newtablinks.auth.repositories.SingleUseCodeRepository;
import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.repositories.EntitlementRepository;
import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import com.kovospace.newtablinks.environment.repositories.EnvironmentRepository;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.repositories.ProfileRepository;
import com.kovospace.newtablinks.user.models.UserDeviceEntity;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.models.UserIdentityEntity;
import com.kovospace.newtablinks.user.repositories.UserDeviceRepository;
import com.kovospace.newtablinks.user.repositories.UserIdentityRepository;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Tests that deleting an account clears every table that names it, in an order the foreign keys
 * tolerate.
 *
 * <p>Nothing cascades from the user row, so what is asserted here is coverage - that no table is
 * forgotten - and the two orderings the schema imposes: a refresh token names its device, and an
 * environment names its profile.</p>
 *
 * @since 0.0.7
 */
class AccountDeletionServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private final HierarchyDeletionService hierarchyDeletionService =
            mock(HierarchyDeletionService.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
    private final RefreshTokenRepository refreshTokenRepository =
            mock(RefreshTokenRepository.class);
    private final UserDeviceRepository userDeviceRepository = mock(UserDeviceRepository.class);
    private final EmailedTokenRepository emailedTokenRepository =
            mock(EmailedTokenRepository.class);
    private final SingleUseCodeRepository singleUseCodeRepository =
            mock(SingleUseCodeRepository.class);
    private final UserIdentityRepository userIdentityRepository =
            mock(UserIdentityRepository.class);
    private final EntitlementRepository entitlementRepository = mock(EntitlementRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final AccountDeletionService accountDeletionService = new AccountDeletionService(
            hierarchyDeletionService, profileRepository, environmentRepository,
            refreshTokenRepository, userDeviceRepository, emailedTokenRepository,
            singleUseCodeRepository, userIdentityRepository, entitlementRepository,
            userRepository);

    private final UserEntity user = mock(UserEntity.class);

    @BeforeEach
    void identifyTheAccount() {
        when(user.getId()).thenReturn(USER_ID);
    }

    @Test
    @DisplayName("clears every table naming the account before removing the account row")
    void clearsEveryTableThatNamesTheAccount() {

        final ProfileEntity profile = mock(ProfileEntity.class);
        final RefreshTokenEntity refreshToken = mock(RefreshTokenEntity.class);
        final UserDeviceEntity device = mock(UserDeviceEntity.class);
        final EmailedTokenEntity emailedToken = mock(EmailedTokenEntity.class);
        final SingleUseCodeEntity singleUseCode = mock(SingleUseCodeEntity.class);
        final UserIdentityEntity identity = mock(UserIdentityEntity.class);

        when(profileRepository.findAllByOwnerIdOrderByPositionAsc(USER_ID))
                .thenReturn(List.of(profile));
        when(refreshTokenRepository.findAllByUserId(USER_ID)).thenReturn(List.of(refreshToken));
        when(userDeviceRepository.findAllByUserIdOrderByLastUsedAtDesc(USER_ID))
                .thenReturn(List.of(device));
        when(emailedTokenRepository.findAllByUserId(USER_ID)).thenReturn(List.of(emailedToken));
        when(singleUseCodeRepository.findAllByUserId(USER_ID)).thenReturn(List.of(singleUseCode));
        when(userIdentityRepository.findAllByUserId(USER_ID)).thenReturn(List.of(identity));
        final EntitlementEntity entitlement = mock(EntitlementEntity.class);
        when(entitlementRepository.findByOwnerId(USER_ID)).thenReturn(Optional.of(entitlement));

        accountDeletionService.deleteAccountWithEverythingItOwns(user);

        verify(hierarchyDeletionService).deleteProfileWithDescendants(profile);
        verify(refreshTokenRepository).deleteAll(List.of(refreshToken));
        verify(userDeviceRepository).deleteAll(List.of(device));
        verify(emailedTokenRepository).deleteAll(List.of(emailedToken));
        verify(singleUseCodeRepository).deleteAll(List.of(singleUseCode));
        verify(userIdentityRepository).deleteAll(List.of(identity));
        verify(entitlementRepository).delete(entitlement);
        verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("removes refresh tokens before the devices they were issued to")
    void removesRefreshTokensBeforeDevices() {

        final RefreshTokenEntity refreshToken = mock(RefreshTokenEntity.class);
        final UserDeviceEntity device = mock(UserDeviceEntity.class);

        when(refreshTokenRepository.findAllByUserId(USER_ID)).thenReturn(List.of(refreshToken));
        when(userDeviceRepository.findAllByUserIdOrderByLastUsedAtDesc(USER_ID))
                .thenReturn(List.of(device));

        accountDeletionService.deleteAccountWithEverythingItOwns(user);

        final InOrder order = inOrder(refreshTokenRepository, userDeviceRepository, userRepository);
        order.verify(refreshTokenRepository).deleteAll(List.of(refreshToken));
        order.verify(userDeviceRepository).deleteAll(List.of(device));
        order.verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("takes an environment down through the hierarchy, never by itself")
    void neverDeletesAStrayEnvironmentDirectly() {

        final EnvironmentEntity stray = mock(EnvironmentEntity.class);
        when(environmentRepository.findAllByOwnerIdOrderByPositionAsc(USER_ID))
                .thenReturn(List.of(stray));

        accountDeletionService.deleteAccountWithEverythingItOwns(user);

        verify(hierarchyDeletionService).deleteEnvironmentWithDescendants(stray);
        verify(environmentRepository, never()).delete(any(EnvironmentEntity.class));
    }

    @Test
    @DisplayName("removes an account that owns nothing")
    void removesAnAccountThatOwnsNothing() {

        accountDeletionService.deleteAccountWithEverythingItOwns(user);

        verify(userRepository).delete(user);
    }
}
