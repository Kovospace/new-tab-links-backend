package com.kovospace.newtablinks.visit.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A visit to a page as returned to a client.
 *
 * @param id         identifier of the visit
 * @param profileId  identifier of the profile whose history it is in
 * @param url        address of the page that was visited
 * @param title      what the page called itself, empty when it never said
 * @param faviconUrl address of the favicon the browser had, {@code null} when there was none
 * @param visitedAt  moment the page was visited, as reported by the device that visited it
 * @param deviceName name of the device the page was visited on, {@code null} when it has none
 * @param createdAt  when the row reached this server
 * @param updatedAt  when the row was last written
 * @since 0.0.19
 */
@Schema(description = "A page the user visited, kept in the profile's browsing history")
public record VisitDto(

        @Schema(description = "Identifier of the visit")
        UUID id,

        @Schema(description = "Identifier of the profile whose history it is in")
        UUID profileId,

        @Schema(description = "Address of the page that was visited", example = "https://spring.io")
        String url,

        @Schema(description = "What the page called itself, empty when it never said",
                example = "Spring Boot reference")
        String title,

        @Schema(description = "Address of the favicon the browser had, null when there was none")
        String faviconUrl,

        @Schema(description = "Moment the page was visited, as reported by the device that "
                + "visited it and never a server clock", example = "2026-10-11T10:15:30Z")
        Instant visitedAt,

        @Schema(description = "Name of the device the page was visited on, null when it has none",
                example = "Laptop")
        String deviceName,

        @Schema(description = "When the row reached this server")
        Instant createdAt,

        @Schema(description = "When the row was last written")
        Instant updatedAt) {
}
