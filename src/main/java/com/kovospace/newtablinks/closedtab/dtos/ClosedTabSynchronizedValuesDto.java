package com.kovospace.newtablinks.closedtab.dtos;

import java.time.Instant;

/**
 * The fields of a closed tab that a pushed synchronization operation replaces wholesale.
 *
 * <p>Unlike the other kinds there is no interactive request DTO beside this one: a closed tab is
 * only ever created by a browser closing a tab, so synchronization is the only way one arrives.
 * </p>
 *
 * <p>{@code closedAt} is the client's value and is stored exactly as it was sent; see
 * {@link com.kovospace.newtablinks.closedtab.models.ClosedTabEntity#getClosedAt()}.</p>
 *
 * @param url        address the tab was showing
 * @param title      what the page called itself, empty when it never said
 * @param faviconUrl address of the favicon the browser had, may be {@code null}
 * @param closedAt   moment the tab was closed, as reported by the device that closed it
 * @param deviceName name of the device the tab was closed on, may be {@code null}
 * @since 0.0.8
 */
public record ClosedTabSynchronizedValuesDto(
        String url,
        String title,
        String faviconUrl,
        Instant closedAt,
        String deviceName) {
}
