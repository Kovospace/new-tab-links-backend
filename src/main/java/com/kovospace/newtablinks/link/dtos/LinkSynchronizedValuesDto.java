package com.kovospace.newtablinks.link.dtos;

/**
 * The fields of a link that a pushed synchronization operation replaces wholesale.
 *
 * <p>Distinct from {@link LinkSaveRequestDto} because synchronization sets the display position
 * as well; see
 * {@link com.kovospace.newtablinks.environment.dtos.EnvironmentSynchronizedValuesDto}.</p>
 *
 * @param title      text shown for the link
 * @param url        address the link points to
 * @param faviconUrl address of the cached favicon, may be {@code null} to have one derived
 * @param position   zero based position among the links of the same parent
 * @since 0.0.6
 */
public record LinkSynchronizedValuesDto(
        String title,
        String url,
        String faviconUrl,
        int position) {
}
