package com.kovospace.newtablinks.user.dtos;

import com.kovospace.newtablinks.user.models.DeviceSyncSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A place the account has been signed in from, as shown to its owner.
 *
 * @param id          identifier of the device, used to sign it out
 * @param deviceName  name of the machine, as the client reported it
 * @param browserName browser on that machine
 * @param firstSeenAt when the account was first used from here
 * @param lastUsedAt  when the account was last used from here
 * @param signedIn    whether this device still holds a usable session
 * @param syncSummary how much of what the installation holds synchronises, from its last
 *                    inventory report; {@code UNKNOWN} without one
 * @param inventoryReportedAt when the installation last reported what it holds; {@code null}
 *                    when it never has
 * @since 0.0.3
 */
@Schema(description = "A place the account has been signed in from")
public record UserDeviceDto(

        @Schema(description = "Identifier of the device, used to sign it out")
        UUID id,

        @Schema(description = "Name of the machine, as the client reported it", example = "Work laptop")
        String deviceName,

        @Schema(description = "Browser on that machine", example = "Firefox")
        String browserName,

        @Schema(description = "When the account was first used from here")
        Instant firstSeenAt,

        @Schema(description = "When the account was last used from here")
        Instant lastUsedAt,

        @Schema(description = "Whether this device still holds a usable session", example = "true")
        boolean signedIn,

        @Schema(description = "How much of what the installation holds synchronises, from its "
                + "last inventory report; example data is ignored, and a device that never "
                + "reported (the website, or an extension that has not yet) is UNKNOWN",
                example = "PARTIAL")
        DeviceSyncSummary syncSummary,

        @Schema(description = "When the installation last reported what it holds; null when it "
                + "never has", nullable = true)
        Instant inventoryReportedAt) {
}
