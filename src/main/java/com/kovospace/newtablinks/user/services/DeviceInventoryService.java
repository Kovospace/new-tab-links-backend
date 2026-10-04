package com.kovospace.newtablinks.user.services;

import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.user.dtos.DeviceInventoryDto;
import com.kovospace.newtablinks.user.dtos.DeviceInventoryReportRequestDto;
import com.kovospace.newtablinks.user.models.DeviceSyncSummary;
import com.kovospace.newtablinks.user.models.UserDeviceEntity;
import com.kovospace.newtablinks.user.repositories.UserDeviceRepository;
import com.kovospace.newtablinks.user.utils.DeviceInventoryCodec;
import com.kovospace.newtablinks.user.utils.DeviceSyncSummaryCalculator;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What each extension installation holds, as it last reported it.
 *
 * <p>The server never sees local-only data - profiles and workspaces beyond the plan's slots, or
 * over a Fair Use cap - so each installation reports its profiles and workspaces (names, sync
 * states and counts; never a link, an address or a group name) after a sync cycle in which they
 * changed. The report is stored on the installation's device row as it was sent, with the moment
 * it arrived, replaced by the next one, and deleted with the device and with the account. The
 * installation decides the sync states; nothing here re-derives them.</p>
 *
 * @since 0.0.18
 */
@Service
public class DeviceInventoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceInventoryService.class);

    private static final String DEVICE_RESOURCE_NAME = "Device";
    private static final String INVENTORY_RESOURCE_NAME = "Inventory of device";

    private final UserDeviceRepository userDeviceRepository;
    private final DeviceInventoryCodec deviceInventoryCodec;

    /**
     * Creates the service.
     *
     * @param userDeviceRepository finds the reporting installation's device
     * @param deviceInventoryCodec turns a report into stored JSON and back
     */
    public DeviceInventoryService(
            final UserDeviceRepository userDeviceRepository,
            final DeviceInventoryCodec deviceInventoryCodec) {

        this.userDeviceRepository = userDeviceRepository;
        this.deviceInventoryCodec = deviceInventoryCodec;
    }

    /**
     * Stores what the installation making the request holds, replacing its previous report.
     *
     * <p>The installation is identified by the identifier it reports in its
     * {@code X-Installation-Id} header, within the account of its access token - an access token
     * names no device, so there is no other way to know which one is asking.</p>
     *
     * @param userId         identifier of the account, from the access token
     * @param installationId the installation reporting, from its header; may be {@code null}
     * @param report         the validated report
     * @throws ResourceNotFoundException when no installation was named, or the account has no
     *                                   device for it
     */
    @Transactional
    public void replaceInventoryOfInstallation(
            final UUID userId,
            final UUID installationId,
            final DeviceInventoryReportRequestDto report) {

        final UserDeviceEntity device = installationId == null
                ? null
                : userDeviceRepository.findByUserIdAndInstallationId(userId, installationId)
                        .orElse(null);
        if (device == null) {
            throw new ResourceNotFoundException(DEVICE_RESOURCE_NAME, installationId);
        }
        device.replaceInventory(deviceInventoryCodec.encode(report.profiles()), Instant.now());
        LOGGER.debug("Device {} of account {} reported {} profile(s)",
                device.getId(), userId, report.profiles().size());
    }

    /**
     * Reads one device's last report, for its owner.
     *
     * @param userId   identifier of the account that must own the device
     * @param deviceId identifier of the device
     * @return the report and when it arrived
     * @throws ResourceNotFoundException when the device does not exist, belongs to somebody else,
     *                                   or has never reported
     */
    @Transactional(readOnly = true)
    public DeviceInventoryDto findInventoryOfDevice(final UUID userId, final UUID deviceId) {
        final UserDeviceEntity device = userDeviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(DEVICE_RESOURCE_NAME, deviceId));
        if (device.getInventoryJson() == null) {
            throw new ResourceNotFoundException(INVENTORY_RESOURCE_NAME, deviceId);
        }
        return new DeviceInventoryDto(
                device.getInventoryReportedAt(),
                deviceInventoryCodec.decode(device.getInventoryJson()));
    }

    /**
     * Summarises how much of what a device holds synchronises, from its last report.
     *
     * @param device the device
     * @return the summary; {@link DeviceSyncSummary#UNKNOWN} when it has never reported
     */
    public DeviceSyncSummary summariseSyncOf(final UserDeviceEntity device) {
        return device.getInventoryJson() == null
                ? DeviceSyncSummary.UNKNOWN
                : DeviceSyncSummaryCalculator.summarise(
                        deviceInventoryCodec.decode(device.getInventoryJson()));
    }
}
