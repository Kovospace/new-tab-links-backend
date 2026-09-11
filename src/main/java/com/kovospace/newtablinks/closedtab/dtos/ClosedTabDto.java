package com.kovospace.newtablinks.closedtab.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A closed tab as returned to a client.
 *
 * @param id         identifier of the closed tab
 * @param profileId  identifier of the profile whose list it is on
 * @param url        address the tab was showing
 * @param title      what the page called itself, empty when it never said
 * @param faviconUrl address of the favicon the browser had, {@code null} when there was none
 * @param closedAt   moment the tab was closed, as reported by the device that closed it
 * @param deviceName name of the device the tab was closed on, {@code null} when it has none
 * @param createdAt  when the row reached this server
 * @param updatedAt  when the row was last written
 * @since 0.0.8
 */
@Schema(description = "A tab the user closed, kept so it can be found again")
public record ClosedTabDto(

        @Schema(description = "Identifier of the closed tab")
        UUID id,

        @Schema(description = "Identifier of the profile whose list it is on")
        UUID profileId,

        @Schema(description = "Address the tab was showing", example = "https://spring.io")
        String url,

        @Schema(description = "What the page called itself, empty when it never said",
                example = "Spring Boot reference")
        String title,

        @Schema(description = "Address of the favicon the browser had, null when there was none")
        String faviconUrl,

        @Schema(description = "Moment the tab was closed, as reported by the device that closed "
                + "it and never a server clock", example = "2026-09-11T10:15:30Z")
        Instant closedAt,

        @Schema(description = "Name of the device the tab was closed on, null when it has none",
                example = "Laptop")
        String deviceName,

        @Schema(description = "When the row reached this server")
        Instant createdAt,

        @Schema(description = "When the row was last written")
        Instant updatedAt) {
}
