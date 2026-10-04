package com.kovospace.newtablinks.user.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * An installation's last inventory report, as shown on the website's device detail page.
 *
 * @param reportedAt when the report was received
 * @param profiles   every profile the installation holds, with its workspaces, as reported
 * @since 0.0.18
 */
@Schema(description = "What one installation last reported holding")
public record DeviceInventoryDto(

        @Schema(description = "When the report was received", example = "2026-10-04T10:15:30Z")
        Instant reportedAt,

        @Schema(description = "Every profile the installation holds, in its order")
        List<DeviceInventoryProfileDto> profiles) {
}
