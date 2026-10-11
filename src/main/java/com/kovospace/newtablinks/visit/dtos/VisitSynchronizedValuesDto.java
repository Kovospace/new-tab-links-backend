package com.kovospace.newtablinks.visit.dtos;

import java.time.Instant;

/**
 * The fields of a visit that a pushed synchronization operation replaces wholesale.
 *
 * <p>There is no interactive request DTO beside this one: a visit is only ever recorded by a
 * browser, so synchronization is the only way one arrives.</p>
 *
 * <p>{@code visitedAt} is the client's value and is stored exactly as it was sent; see
 * {@link com.kovospace.newtablinks.visit.models.VisitEntity#getVisitedAt()}.</p>
 *
 * @param url        address of the page that was visited
 * @param title      what the page called itself, empty when it never said
 * @param faviconUrl address of the favicon the browser had, may be {@code null}
 * @param visitedAt  moment the page was visited, as reported by the device that visited it
 * @param deviceName name of the device the page was visited on, may be {@code null}
 * @since 0.0.19
 */
public record VisitSynchronizedValuesDto(
        String url,
        String title,
        String faviconUrl,
        Instant visitedAt,
        String deviceName) {
}
