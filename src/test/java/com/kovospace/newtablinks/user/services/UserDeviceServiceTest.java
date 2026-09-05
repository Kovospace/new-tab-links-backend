package com.kovospace.newtablinks.user.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.auth.dtos.ClientDescriptionDto;
import com.kovospace.newtablinks.auth.repositories.RefreshTokenRepository;
import com.kovospace.newtablinks.user.models.UserDeviceEntity;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserDeviceRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests what a sign-in is matched against, which is the whole of this bug.
 *
 * <p>The names a client sends cannot tell two browsers on one machine apart - a device name comes
 * from a frozen {@code navigator.platform} and Chromium forks impersonate Chrome in the user
 * agent - so what matters here is that two installations sending identical names are two devices,
 * and that a client sending no installation at all still behaves as it always did.</p>
 *
 * @since 0.0.7
 */
class UserDeviceServiceTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final String SHARED_DEVICE_NAME = "Browser extension on Linux x86_64";
    private static final String SHARED_BROWSER_NAME = "Chrome";

    private final UserDeviceRepository userDeviceRepository = mock(UserDeviceRepository.class);
    private final RefreshTokenRepository refreshTokenRepository =
            mock(RefreshTokenRepository.class);

    private final UserDeviceService userDeviceService =
            new UserDeviceService(userDeviceRepository, refreshTokenRepository);

    private final UserEntity account = mock(UserEntity.class);

    @BeforeEach
    void identifyTheAccount() {
        when(account.getId()).thenReturn(ACCOUNT_ID);
        when(userDeviceRepository.save(any(UserDeviceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("records two installations sending identical names as two devices")
    void twoInstallationsWithTheSameNamesAreTwoDevices() {

        final UUID firstInstallation = UUID.randomUUID();
        final UUID secondInstallation = UUID.randomUUID();

        final UserDeviceEntity firstDevice =
                userDeviceService.recordDeviceUse(account, chromiumInstall(firstInstallation));

        // The first one is now on record, and the second one finds it by name - but must not
        // take it, because that row already belongs to somebody.
        when(userDeviceRepository.findByUserIdAndInstallationId(ACCOUNT_ID, firstInstallation))
                .thenReturn(Optional.of(firstDevice));
        when(userDeviceRepository.findByUserIdAndDeviceNameAndBrowserName(
                ACCOUNT_ID, SHARED_DEVICE_NAME, SHARED_BROWSER_NAME))
                .thenReturn(Optional.of(firstDevice));

        final UserDeviceEntity secondDevice =
                userDeviceService.recordDeviceUse(account, chromiumInstall(secondInstallation));

        assertThat(secondDevice).isNotSameAs(firstDevice);
        assertThat(firstDevice.getInstallationId()).isEqualTo(firstInstallation);
        assertThat(secondDevice.getInstallationId()).isEqualTo(secondInstallation);
    }

    @Test
    @DisplayName("reuses the row of an installation that has signed in before")
    void reusesTheRowOfAKnownInstallation() {

        final UUID installation = UUID.randomUUID();
        final UserDeviceEntity known = new UserDeviceEntity(
                account, SHARED_DEVICE_NAME, SHARED_BROWSER_NAME, installation, Instant.now());

        when(userDeviceRepository.findByUserIdAndInstallationId(ACCOUNT_ID, installation))
                .thenReturn(Optional.of(known));

        final UserDeviceEntity resolved =
                userDeviceService.recordDeviceUse(account, chromiumInstall(installation));

        assertThat(resolved).isSameAs(known);
        verify(userDeviceRepository, never()).save(any(UserDeviceEntity.class));
    }

    @Test
    @DisplayName("relabels a known installation whose browser or machine name has changed")
    void relabelsAKnownInstallation() {

        final UUID installation = UUID.randomUUID();
        final UserDeviceEntity known = new UserDeviceEntity(
                account, "Browser extension on Linux i686", "Chromium", installation,
                Instant.now());

        when(userDeviceRepository.findByUserIdAndInstallationId(ACCOUNT_ID, installation))
                .thenReturn(Optional.of(known));

        userDeviceService.recordDeviceUse(account, chromiumInstall(installation));

        assertThat(known.getDeviceName()).isEqualTo(SHARED_DEVICE_NAME);
        assertThat(known.getBrowserName()).isEqualTo(SHARED_BROWSER_NAME);
    }

    @Test
    @DisplayName("claims a device recorded before installations were reported")
    void claimsAnUnattributedDeviceInsteadOfDuplicatingIt() {

        final UUID installation = UUID.randomUUID();
        final UserDeviceEntity legacy = new UserDeviceEntity(
                account, SHARED_DEVICE_NAME, SHARED_BROWSER_NAME, null, Instant.now());

        when(userDeviceRepository.findByUserIdAndInstallationId(ACCOUNT_ID, installation))
                .thenReturn(Optional.empty());
        when(userDeviceRepository.findByUserIdAndDeviceNameAndBrowserName(
                ACCOUNT_ID, SHARED_DEVICE_NAME, SHARED_BROWSER_NAME))
                .thenReturn(Optional.of(legacy));

        final UserDeviceEntity resolved =
                userDeviceService.recordDeviceUse(account, chromiumInstall(installation));

        assertThat(resolved).isSameAs(legacy);
        assertThat(legacy.getInstallationId()).isEqualTo(installation);
        verify(userDeviceRepository, never()).save(any(UserDeviceEntity.class));
    }

    @Test
    @DisplayName("matches a client that reports no installation on its names, as before")
    void keepsTheNameBasedBehaviourForAClientWithoutAnInstallation() {

        final UserDeviceEntity website = new UserDeviceEntity(
                account, "NewTabLinks (production)", "Firefox", null, Instant.now());

        when(userDeviceRepository.findByUserIdAndDeviceNameAndBrowserName(
                ACCOUNT_ID, "NewTabLinks (production)", "Firefox"))
                .thenReturn(Optional.of(website));

        final UserDeviceEntity resolved = userDeviceService.recordDeviceUse(
                account,
                new ClientDescriptionDto("NewTabLinks (production)", "Firefox", null));

        assertThat(resolved).isSameAs(website);
        verify(userDeviceRepository, never()).findByUserIdAndInstallationId(any(), any());
    }

    @Test
    @DisplayName("stores no installation for a client that reports none")
    void storesNoInstallationForAClientThatReportsNone() {

        userDeviceService.recordDeviceUse(
                account,
                new ClientDescriptionDto("NewTabLinks (production)", "Firefox", null));

        final ArgumentCaptor<UserDeviceEntity> saved =
                ArgumentCaptor.forClass(UserDeviceEntity.class);
        verify(userDeviceRepository).save(saved.capture());
        assertThat(saved.getValue().getInstallationId()).isNull();
    }

    /** A Chromium install on Linux: the names every one of them sends, and its own identity. */
    private static ClientDescriptionDto chromiumInstall(final UUID installationId) {
        return new ClientDescriptionDto(SHARED_DEVICE_NAME, SHARED_BROWSER_NAME, installationId);
    }
}
