package com.kovospace.newtablinks.user.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.auth.dtos.ClientDescriptionDto;
import com.kovospace.newtablinks.auth.models.RefreshTokenEntity;
import com.kovospace.newtablinks.auth.repositories.RefreshTokenRepository;
import com.kovospace.newtablinks.user.models.UserDeviceEntity;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserDeviceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

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

    @Test
    @DisplayName("keeps the taken-over row and removes the one the taker was given")
    void takeOverKeepsTheRecognisedRowAndDropsTheAccidentalOne() {

        final UUID taker = UUID.randomUUID();
        final UserDeviceEntity target = deviceOf(UUID.randomUUID(), "Work desktop", "Firefox");
        final UserDeviceEntity takersOwn = deviceOf(taker, SHARED_DEVICE_NAME, SHARED_BROWSER_NAME);
        final RefreshTokenEntity takersToken = mock(RefreshTokenEntity.class);

        when(userDeviceRepository.findByIdAndUserId(target.getId(), ACCOUNT_ID))
                .thenReturn(Optional.of(target));
        when(userDeviceRepository.findByUserIdAndInstallationId(ACCOUNT_ID, taker))
                .thenReturn(Optional.of(takersOwn));
        when(refreshTokenRepository.findAllByDeviceId(takersOwn.getId()))
                .thenReturn(List.of(takersToken));

        userDeviceService.takeOverDevice(target.getId(), ACCOUNT_ID, taker);

        assertThat(target.getInstallationId()).isEqualTo(taker);
        assertThat(target.getDeviceName()).isEqualTo("Work desktop");
        assertThat(target.getBrowserName()).isEqualTo(SHARED_BROWSER_NAME);
        verify(takersToken).moveToDevice(target);
        verify(userDeviceRepository).delete(takersOwn);
    }

    @Test
    @DisplayName("revokes the old installation's tokens before moving the taker's across")
    void takeOverRevokesBeforeMovingSoTheTakerIsNotSignedOut() {

        final UUID taker = UUID.randomUUID();
        final UserDeviceEntity target = deviceOf(UUID.randomUUID(), "Work desktop", "Firefox");
        final UserDeviceEntity takersOwn = deviceOf(taker, SHARED_DEVICE_NAME, SHARED_BROWSER_NAME);

        when(userDeviceRepository.findByIdAndUserId(target.getId(), ACCOUNT_ID))
                .thenReturn(Optional.of(target));
        when(userDeviceRepository.findByUserIdAndInstallationId(ACCOUNT_ID, taker))
                .thenReturn(Optional.of(takersOwn));
        when(refreshTokenRepository.findAllByDeviceId(takersOwn.getId())).thenReturn(List.of());

        userDeviceService.takeOverDevice(target.getId(), ACCOUNT_ID, taker);

        // Revoking after the move would revoke the session the request is being made with, and
        // the flush is what lets the reassignment below it happen at all: both rows hold the
        // taker's installation until the old one is really gone, and the unique index on
        // (user_id, installation_id) rejects that pair. A mock has no such index, so this
        // ordering is the only thing here that can catch its loss.
        final InOrder order = inOrder(refreshTokenRepository, userDeviceRepository);
        order.verify(refreshTokenRepository).revokeAllLiveTokensOfDevice(any(), any());
        order.verify(refreshTokenRepository).findAllByDeviceId(takersOwn.getId());
        order.verify(userDeviceRepository).delete(takersOwn);
        order.verify(userDeviceRepository).flush();
        assertThat(target.getInstallationId()).isEqualTo(taker);
    }

    @Test
    @DisplayName("does nothing when the installation already owns the device")
    void takeOverOfAnAlreadyOwnedDeviceIsANoOp() {

        final UUID owner = UUID.randomUUID();
        final UserDeviceEntity mine = deviceOf(owner, "Work desktop", "Firefox");

        when(userDeviceRepository.findByIdAndUserId(mine.getId(), ACCOUNT_ID))
                .thenReturn(Optional.of(mine));

        userDeviceService.takeOverDevice(mine.getId(), ACCOUNT_ID, owner);

        // A client retrying after a lost response must not be punished for it.
        verify(refreshTokenRepository, never()).revokeAllLiveTokensOfDevice(any(), any());
        verify(userDeviceRepository, never()).delete(any(UserDeviceEntity.class));
    }

    @Test
    @DisplayName("renames a device without touching what identifies it")
    void renameChangesOnlyTheLabel() {

        final UUID installation = UUID.randomUUID();
        final UserDeviceEntity device =
                deviceOf(installation, SHARED_DEVICE_NAME, SHARED_BROWSER_NAME);

        when(userDeviceRepository.findByIdAndUserId(device.getId(), ACCOUNT_ID))
                .thenReturn(Optional.of(device));

        userDeviceService.renameDevice(device.getId(), ACCOUNT_ID, "Work desktop");

        assertThat(device.getDeviceName()).isEqualTo("Work desktop");
        assertThat(device.getBrowserName()).isEqualTo(SHARED_BROWSER_NAME);
        assertThat(device.getInstallationId()).isEqualTo(installation);
    }

    /** A stored device with an identifier of its own, which take-over needs to tell rows apart. */
    private UserDeviceEntity deviceOf(
            final UUID installationId, final String deviceName, final String browserName) {

        final UserDeviceEntity device = new UserDeviceEntity(
                account, deviceName, browserName, installationId, Instant.now());
        device.setId(UUID.randomUUID());
        return device;
    }

    /** A Chromium install on Linux: the names every one of them sends, and its own identity. */
    private static ClientDescriptionDto chromiumInstall(final UUID installationId) {
        return new ClientDescriptionDto(SHARED_DEVICE_NAME, SHARED_BROWSER_NAME, installationId);
    }
}
