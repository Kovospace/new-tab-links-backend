package com.kovospace.newtablinks.statistics.utils;

import java.util.regex.Pattern;

/**
 * Tells a browser apart from a crawler, a link preview or a script, by its {@code User-Agent}.
 *
 * <p>Only the honest ones: anything can claim to be a browser, and this does not try to catch
 * what lies. It keeps the visitor count from being mostly search engines and uptime monitors,
 * which is all it is for.</p>
 *
 * @since 0.0.11
 */
public final class AutomatedUserAgentDetector {

    /**
     * Words that only automated clients put in their {@code User-Agent}. {@code java/} carries
     * its slash so that no browser mentioning JavaScript is caught.
     */
    private static final Pattern AUTOMATED_USER_AGENT = Pattern.compile(
            "(?i)bot|crawl|spider|slurp|headless|preview|scan|monitor|curl|wget|python|java/"
                    + "|go-http|httpclient");

    /**
     * Prevents instantiation of this utility class.
     */
    private AutomatedUserAgentDetector() {
    }

    /**
     * Whether a request should not be counted as a human visit.
     *
     * @param userAgent the request's {@code User-Agent}; {@code null} when absent
     * @return {@code true} when the header is missing, blank, or names an automated client
     */
    public static boolean isMissingOrAutomated(final String userAgent) {
        return userAgent == null
                || userAgent.isBlank()
                || AUTOMATED_USER_AGENT.matcher(userAgent).find();
    }
}
