package com.kovospace.newtablinks.user.repositories;

import com.kovospace.newtablinks.user.models.UserDeviceEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link UserDeviceEntity}.
 *
 * @since 0.0.3
 */
@Repository
public interface UserDeviceRepository extends JpaRepository<UserDeviceEntity, UUID> {

    /**
     * Finds a user's device recorded by name only - one no installation has claimed - by the
     * pair of names it was recorded under.
     *
     * <p>Only unattributed rows are searched, because only they are unique by name
     * ({@code uk_user_device_unattributed}): any number of installations may send the same names
     * - every Chromium install on one Linux machine does - and searching their rows too returned
     * several results once a third such installation signed in.</p>
     *
     * @param userId      identifier of the owning account
     * @param deviceName  name of the machine
     * @param browserName browser on that machine
     * @return the matching device, or an empty optional when no unattributed row carries those
     *         names
     */
    Optional<UserDeviceEntity> findByUserIdAndDeviceNameAndBrowserNameAndInstallationIdIsNull(
            UUID userId, String deviceName, String browserName);

    /**
     * Lists a user's devices, most recently used first.
     *
     * @param userId identifier of the owning account
     * @return the devices, empty when there are none
     */
    /**
     * Finds the device an installation reported, whatever it currently calls itself.
     *
     * <p>This is the identity lookup. The name-based one below it is the fallback for clients
     * that report no installation.</p>
     *
     * @param userId         identifier of the owning user
     * @param installationId identifier the client installation minted for itself
     * @return the device, or an empty optional when this installation is new to the account
     */
    Optional<UserDeviceEntity> findByUserIdAndInstallationId(UUID userId, UUID installationId);

    List<UserDeviceEntity> findAllByUserIdOrderByLastUsedAtDesc(UUID userId);

    /**
     * Finds one of a user's devices, enforcing ownership in the query.
     *
     * @param deviceId identifier of the device
     * @param userId   identifier of the account that must own it
     * @return the device, or an empty optional when it does not exist or is not theirs
     */
    Optional<UserDeviceEntity> findByIdAndUserId(UUID deviceId, UUID userId);

    /**
     * Counts an account's synchronised installations: devices recorded for a client that reported
     * its own installation identifier.
     *
     * <p>The extension reports one on every token request; the website reports none, so its
     * sign-ins are recorded by name only and are not counted. Signed-out devices are counted -
     * they stay listed until forgotten.</p>
     *
     * @param userId identifier of the owning account
     * @return the count, zero when there are none
     * @since 0.0.17
     */
    long countByUserIdAndInstallationIdIsNotNull(UUID userId);
}
