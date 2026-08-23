package com.kovospace.newtablinks.link.utils;

import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Derives the address of a site's favicon from the address of one of its pages.
 *
 * <p>The browser extension resolves and caches favicons itself, so a client usually sends one
 * along with the link. When it does not, this fallback guesses the conventional
 * {@code /favicon.ico} at the site root, which is correct for the large majority of sites and
 * harmless when it is not - a favicon that fails to load simply renders as the default icon.</p>
 *
 * @since 0.0.1
 */
public final class FaviconUrlResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaviconUrlResolver.class);

    /**
     * Path at which sites conventionally serve their icon.
     */
    private static final String CONVENTIONAL_FAVICON_PATH = "/favicon.ico";

    /**
     * Not instantiable; this class only holds static helpers.
     */
    private FaviconUrlResolver() {
        throw new AssertionError("FaviconUrlResolver is a utility class and must not be instantiated");
    }

    /**
     * Returns the favicon address to store for a link.
     *
     * @param faviconUrlFromClient favicon address supplied by the client, may be {@code null} or blank
     * @param linkUrl              address the link points to, used to derive a fallback
     * @return the client's address when it supplied one, otherwise the derived conventional
     *         address, or {@code null} when nothing usable could be derived
     */
    public static String resolveFaviconUrl(final String faviconUrlFromClient, final String linkUrl) {
        if (faviconUrlFromClient != null && !faviconUrlFromClient.isBlank()) {
            return faviconUrlFromClient;
        }
        return deriveConventionalFaviconUrl(linkUrl);
    }

    /**
     * Builds {@code scheme://host[:port]/favicon.ico} from a page address.
     *
     * @param linkUrl address to derive from
     * @return the derived address, or {@code null} when the input is not a usable absolute URL
     */
    private static String deriveConventionalFaviconUrl(final String linkUrl) {
        if (linkUrl == null || linkUrl.isBlank()) {
            return null;
        }
        try {
            final URI parsedLinkUrl = new URI(linkUrl);
            if (parsedLinkUrl.getScheme() == null || parsedLinkUrl.getHost() == null) {
                return null;
            }
            return new URI(
                    parsedLinkUrl.getScheme(),
                    null,
                    parsedLinkUrl.getHost(),
                    parsedLinkUrl.getPort(),
                    CONVENTIONAL_FAVICON_PATH,
                    null,
                    null).toString();
        } catch (final URISyntaxException malformedUrl) {
            LOGGER.debug("Could not derive a favicon address from {}", linkUrl);
            return null;
        }
    }
}
