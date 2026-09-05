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
import java.util.Optional;
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
     * <p>Signing in again from the same installation updates that row instead of adding another,
     * which is what keeps the user's list short and meaningful. A client that reports no
     * installation - the website, or an extension older than the header - is matched on its names
     * as it always was.</p>
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

        final UserDeviceEntity device = clientDescription.installationId() == null
                ? findOrRecordByName(account, clientDescription, now)
                : findOrRecordByInstallation(account, clientDescription, now);

        device.markUsedAt(now);
        return device;
    }

    /**
     * Resolves a device by the installation that reported it.
     *
     * <p>Three cases, in the order they are tried. The installation is already known, and its row
     * is relabelled in case the browser was upgraded or the machine renamed - which only became a
     * sensible thing to do once the names stopped being the identity. Or the installation is new
     * but its names match a row nobody has claimed, in which case it claims it: that is how a
     * device recorded before installations were reported survives, rather than turning into a
     * duplicate beside itself. Or it is genuinely new.</p>
     *
     * @param account           account signing in
     * @param clientDescription where the sign-in is coming from, installation included
     * @param now               moment of this sign-in
     * @return the managed device
     */
    private UserDeviceEntity findOrRecordByInstallation(
            final UserEntity account,
            final ClientDescriptionDto clientDescription,
            final Instant now) {

        final Optional<UserDeviceEntity> knownInstallation = userDeviceRepository
                .findByUserIdAndInstallationId(
                        account.getId(), clientDescription.installationId());

        if (knownInstallation.isPresent()) {
            final UserDeviceEntity device = knownInstallation.get();
            device.relabel(clientDescription.deviceName(), clientDescription.browserName());
            return device;
        }

        final Optional<UserDeviceEntity> unclaimedMatch = findByName(account, clientDescription)
                .filter(candidate -> candidate.getInstallationId() == null);

        if (unclaimedMatch.isPresent()) {
            final UserDeviceEntity device = unclaimedMatch.get();
            device.attributeToInstallation(clientDescription.installationId());
            LOGGER.info("Account {} claimed existing device {} for installation {}",
                    account.getId(), device.getId(), clientDescription.installationId());
            return device;
        }

        LOGGER.info("Account {} signed in from a new installation {}: {} / {}",
                account.getId(),
                clientDescription.installationId(),
                clientDescription.deviceName(),
                clientDescription.browserName());

        return userDeviceRepository.save(new UserDeviceEntity(
                account,
                clientDescription.deviceName(),
                clientDescription.browserName(),
                clientDescription.installationId(),
                now));
    }

    /**
     * Resolves a device for a client that reports no installation, by the names it sends.
     *
     * <p>Unchanged behaviour, and the reason the website's rows still work: it signs in as itself
     * and has no installation identity to report.</p>
     *
     * @param account           account signing in
     * @param clientDescription where the sign-in is coming from
     * @param now               moment of this sign-in
     * @return the managed device
     */
    private UserDeviceEntity findOrRecordByName(
            final UserEntity account,
            final ClientDescriptionDto clientDescription,
            final Instant now) {

        return findByName(account, clientDescription).orElseGet(() -> {
            LOGGER.info("Account {} signed in from a new device: {} / {}",
                    account.getId(),
                    clientDescription.deviceName(),
                    clientDescription.browserName());
            return userDeviceRepository.save(new UserDeviceEntity(
                    account,
                    clientDescription.deviceName(),
                    clientDescription.browserName(),
                    null,
                    now));
        });
    }

    /**
     * Looks a device up by the pair of names a client sends.
     *
     * @param account           account signing in
     * @param clientDescription where the sign-in is coming from
     * @return the device, or an empty optional when no row carries those names
     */
    private Optional<UserDeviceEntity> findByName(
            final UserEntity account,
            final ClientDescriptionDto clientDescription) {

        return userDeviceRepository.findByUserIdAndDeviceNameAndBrowserName(
                account.getId(),
                clientDescription.deviceName(),
                clientDescription.browserName());
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
     * Renames a device.
     *
     * <p>Duplicate names are allowed and not checked for. A device is identified by the
     * installation that reported it, so two devices sharing a label are still two devices and
     * nothing about them merges - which is what lets a client offer a free-text name at all. A
     * client that would rather its user avoided a duplicate can see the existing names in the
     * device list and say so itself; that is a matter of presentation, not of correctness.</p>
     *
     * @param deviceId identifier of the device to rename
     * @param userId   identifier of the account that must own it
     * @param newName  what to call it from now on
     * @throws ResourceNotFoundException when it does not exist or belongs to somebody else
     */
    @Transactional
    public void renameDevice(final UUID deviceId, final UUID userId, final String newName) {
        final UserDeviceEntity device = userDeviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, deviceId));

        device.relabel(newName, device.getBrowserName());
        LOGGER.info("Device {} of account {} renamed", deviceId, userId);
    }

    /**
     * Hands a device row over to the installation making the request.
     *
     * <p>This is what "I have reinstalled on this machine" means once a device is identified by
     * its installation rather than by its name. The row the user recognises is kept, with its
     * history and the date it was first seen; the installation that used to hold it is signed
     * out, and the row this installation was given moments ago is removed, because it was only
     * ever an accident of signing in before the user said which device this is.</p>
     *
     * <p>Order matters and none of it is interchangeable. The target's live tokens are revoked
     * first, while they are still the only ones pointing at it - revoking after the move would
     * sign the taker out of the session it is making the request with. The taker's tokens are
     * then moved across, and only then is the emptied row safe to delete.</p>
     *
     * <p>The installation is reassigned <em>last</em>, after that delete has reached the
     * database, and the flush that makes it do so is not removable. Both rows hold the taker's
     * installation identifier for as long as the old one exists, and
     * {@code uk_user_device_installation} permits one row per account and installation - so
     * assigning it any earlier raises a duplicate key. That is what happens by default, because
     * Hibernate orders every update ahead of every delete when it flushes.</p>
     *
     * @param targetDeviceId identifier of the device to take over
     * @param userId         identifier of the account that must own it
     * @param installationId installation making the request, taken from its header
     * @throws ResourceNotFoundException when the target does not exist or is somebody else's
     */
    @Transactional
    public void takeOverDevice(
            final UUID targetDeviceId,
            final UUID userId,
            final UUID installationId) {

        final UserDeviceEntity target = userDeviceRepository
                .findByIdAndUserId(targetDeviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, targetDeviceId));

        if (installationId == null || installationId.equals(target.getInstallationId())) {
            // Either the caller has no installation identity to take anything over with, or it
            // already owns the row. Both are a no-op rather than an error: repeating a take-over
            // that already happened must not fail, because a client retrying after a lost
            // response cannot tell the difference.
            return;
        }

        final Optional<UserDeviceEntity> takersOwnRow =
                userDeviceRepository.findByUserIdAndInstallationId(userId, installationId);

        refreshTokenRepository.revokeAllLiveTokensOfDevice(target.getId(), Instant.now());

        takersOwnRow.ifPresent(surrendered -> {
            refreshTokenRepository.findAllByDeviceId(surrendered.getId())
                    .forEach(token -> token.moveToDevice(target));
            target.relabel(target.getDeviceName(), surrendered.getBrowserName());
            userDeviceRepository.delete(surrendered);
            // Frees the installation identifier before it is claimed again below. See the note
            // on this method: without it the unique index rejects the pair while both rows hold
            // it, because Hibernate orders every update ahead of every delete in a flush.
            userDeviceRepository.flush();
        });

        target.reassignToInstallation(installationId);

        LOGGER.info("Installation {} took over device {} of account {}",
                installationId, targetDeviceId, userId);
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
