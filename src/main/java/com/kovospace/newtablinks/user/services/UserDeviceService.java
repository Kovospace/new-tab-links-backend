package com.kovospace.newtablinks.user.services;

import com.kovospace.newtablinks.auth.dtos.ClientDescriptionDto;
import com.kovospace.newtablinks.auth.repositories.RefreshTokenRepository;
import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.user.dtos.UserDeviceDto;
import com.kovospace.newtablinks.user.models.UserDeviceEntity;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserDeviceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The list of places an account has been used, and the ability to sign one of them out.
 *
 * @since 0.0.3
 */
@Service
public class UserDeviceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDeviceService.class);
    private static final String RESOURCE_NAME = "Device";

    private final UserDeviceRepository userDeviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Creates the service.
     *
     * @param userDeviceRepository   stores devices
     * @param refreshTokenRepository used to tell a live device from a historical one
     */
    public UserDeviceService(
            final UserDeviceRepository userDeviceRepository,
            final RefreshTokenRepository refreshTokenRepository) {

        this.userDeviceRepository = userDeviceRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Finds the device a sign-in came from, recording it if it has not been seen before.
     *
     * <p>Signing in again from the same machine and browser updates that row instead of adding
     * another, which is what keeps the user's list short and meaningful.</p>
     *
     * @param account           account signing in
     * @param clientDescription where the sign-in is coming from
     * @return the managed device
     */
    @Transactional
    public UserDeviceEntity recordDeviceUse(
            final UserEntity account,
            final ClientDescriptionDto clientDescription) {

        final Instant now = Instant.now();

        return userDeviceRepository
                .findByUserIdAndDeviceNameAndBrowserName(
                        account.getId(),
                        clientDescription.deviceName(),
                        clientDescription.browserName())
                .map(existingDevice -> {
                    existingDevice.markUsedAt(now);
                    return existingDevice;
                })
                .orElseGet(() -> {
                    LOGGER.info("Account {} signed in from a new device: {} / {}",
                            account.getId(),
                            clientDescription.deviceName(),
                            clientDescription.browserName());
                    return userDeviceRepository.save(new UserDeviceEntity(
                            account,
                            clientDescription.deviceName(),
                            clientDescription.browserName(),
                            now));
                });
    }

    /**
     * Lists the places an account has been used, most recent first.
     *
     * <p>Includes devices whose sessions have ended: the question this answers is "where has my
     * account been used", not "where am I signed in right now". {@link UserDeviceDto#signedIn()}
     * distinguishes the two.</p>
     *
     * @param userId identifier of the account
     * @return the devices, empty when there are none
     */
    @Transactional(readOnly = true)
    public List<UserDeviceDto> findDevicesOfUser(final UUID userId) {
        final Set<UUID> devicesWithLiveTokens = refreshTokenRepository
                .findAllByUserIdAndRevokedAtIsNull(userId)
                .stream()
                .filter(token -> token.isUsableAt(Instant.now()))
                .map(token -> token.getDevice().getId())
                .collect(Collectors.toSet());

        return userDeviceRepository.findAllByUserIdOrderByLastUsedAtDesc(userId)
                .stream()
                .map(device -> new UserDeviceDto(
                        device.getId(),
                        device.getDeviceName(),
                        device.getBrowserName(),
                        device.getCreatedAt(),
                        device.getLastUsedAt(),
                        devicesWithLiveTokens.contains(device.getId())))
                .toList();
    }

    /**
     * Signs one device out by revoking every token it holds.
     *
     * <p>The device itself is kept, so the history of where the account has been used survives
     * being signed out.</p>
     *
     * @param deviceId identifier of the device to sign out
     * @param userId   identifier of the account that must own it
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional
    public void signOutDevice(final UUID deviceId, final UUID userId) {
        final UserDeviceEntity device = userDeviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, deviceId));

        final int revoked = refreshTokenRepository.revokeAllLiveTokensOfDevice(
                device.getId(), Instant.now());

        LOGGER.info("Signed out device {} of account {}, revoking {} token(s)",
                deviceId, userId, revoked);
    }
}
