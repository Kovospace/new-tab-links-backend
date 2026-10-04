package com.kovospace.newtablinks.user.utils;

import com.kovospace.newtablinks.user.dtos.DeviceInventoryProfileDto;
import com.kovospace.newtablinks.user.models.DeviceSyncSummary;
import com.kovospace.newtablinks.user.models.InventorySyncState;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reduces an inventory report to one {@link DeviceSyncSummary} for the devices list.
 *
 * <p>Every profile and every workspace counts as one item; items in
 * {@link InventorySyncState#EXAMPLE} are ignored. All remaining items synchronised is
 * {@link DeviceSyncSummary#SYNCHRONISED} - so is a report with nothing but examples, since
 * nothing is held back - none synchronised is {@link DeviceSyncSummary#NOT_SYNCHRONISED}, and
 * anything in between is {@link DeviceSyncSummary#PARTIAL}.</p>
 *
 * @since 0.0.18
 */
public final class DeviceSyncSummaryCalculator {

    /**
     * Not instantiable; static functions only.
     */
    private DeviceSyncSummaryCalculator() {
    }

    /**
     * Summarises a report.
     *
     * @param profiles the reported profiles, or {@code null} when the installation never reported
     * @return the summary; {@link DeviceSyncSummary#UNKNOWN} without a report
     */
    public static DeviceSyncSummary summarise(final List<DeviceInventoryProfileDto> profiles) {
        if (profiles == null) {
            return DeviceSyncSummary.UNKNOWN;
        }
        final List<InventorySyncState> nonExampleStates = profiles.stream()
                .flatMap(DeviceSyncSummaryCalculator::statesOfProfileAndItsWorkspaces)
                .filter(state -> state != InventorySyncState.EXAMPLE)
                .toList();
        final long synchronisedCount = nonExampleStates.stream()
                .filter(state -> state == InventorySyncState.SYNCHRONISED)
                .count();

        if (synchronisedCount == nonExampleStates.size()) {
            return DeviceSyncSummary.SYNCHRONISED;
        }
        return synchronisedCount == 0
                ? DeviceSyncSummary.NOT_SYNCHRONISED
                : DeviceSyncSummary.PARTIAL;
    }

    /**
     * Lists the sync states of a profile and of each of its workspaces.
     *
     * @param profile the reported profile
     * @return its own state first, then one per workspace
     */
    private static Stream<InventorySyncState> statesOfProfileAndItsWorkspaces(
            final DeviceInventoryProfileDto profile) {

        return Stream.concat(
                Stream.of(profile.syncState()),
                profile.workspaces().stream().map(workspace -> workspace.syncState()));
    }
}
