package com.kovospace.newtablinks.auth.utils;

import java.util.Locale;

/**
 * Works out a browser's name from a {@code User-Agent} header.
 *
 * <p>Only enough to label a row in the user's device list. Deliberately crude: user agent strings
 * are a swamp of historical impersonation - every browser claims to be Mozilla, most claim to be
 * Safari, Chromium forks claim to be Chrome - so the order of the checks below matters more than
 * the patterns themselves. The more specific a browser's marker, the earlier it must be tested.</p>
 *
 * <p>Nothing depends on the answer being right. It is a label, never a security decision.</p>
 *
 * @since 0.0.3
 */
public final class BrowserNameParser {

    /**
     * Returned when the header is missing or matches nothing known.
     */
    public static final String UNKNOWN_BROWSER = "Unknown browser";

    /**
     * Longest header considered; anything beyond this is noise or an attempt to fill the column.
     */
    private static final int MAXIMUM_CONSIDERED_LENGTH = 512;

    /**
     * Not instantiable; this class only holds static helpers.
     */
    private BrowserNameParser() {
        throw new AssertionError("BrowserNameParser is a utility class and must not be instantiated");
    }

    /**
     * Names the browser behind a user agent string.
     *
     * @param userAgentHeader the header value, may be {@code null}
     * @return a short browser name, or {@link #UNKNOWN_BROWSER}
     */
    public static String parseBrowserName(final String userAgentHeader) {
        if (userAgentHeader == null || userAgentHeader.isBlank()) {
            return UNKNOWN_BROWSER;
        }

        final String header = userAgentHeader.length() > MAXIMUM_CONSIDERED_LENGTH
                ? userAgentHeader.substring(0, MAXIMUM_CONSIDERED_LENGTH)
                : userAgentHeader;
        final String lowerCased = header.toLowerCase(Locale.ROOT);

        // Order is load-bearing: each of these also matches the ones below it.
        if (lowerCased.contains("edg/") || lowerCased.contains("edga/")) {
            return "Edge";
        }
        if (lowerCased.contains("opr/") || lowerCased.contains("opera")) {
            return "Opera";
        }
        if (lowerCased.contains("vivaldi")) {
            return "Vivaldi";
        }
        if (lowerCased.contains("brave")) {
            return "Brave";
        }
        if (lowerCased.contains("chrome") || lowerCased.contains("chromium")) {
            return "Chrome";
        }
        if (lowerCased.contains("firefox") || lowerCased.contains("fxios")) {
            return "Firefox";
        }
        if (lowerCased.contains("safari")) {
            return "Safari";
        }
        return UNKNOWN_BROWSER;
    }
}
