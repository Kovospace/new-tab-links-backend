package com.kovospace.newtablinks.statistics.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks which user agents count as a human visitor.
 *
 * @since 0.0.11
 */
class AutomatedUserAgentDetectorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
            "Mozilla/5.0 (compatible; bingbot/2.0)",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 HeadlessChrome/128.0",
            "facebookexternalhit/1.1 Facebot Twitterbot/1.0",
            "Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)",
            "curl/8.5.0",
            "Wget/1.21",
            "python-requests/2.32",
            "Java/21.0.2",
            "Go-http-client/2.0",
            "Apache-HttpClient/5.3",
            "UptimeRobot/2.0 (monitor)"})
    @DisplayName("does not count a missing, blank or automated user agent")
    void shouldTreatMissingOrAutomatedUserAgentAsAutomated(final String userAgent) {
        assertThat(AutomatedUserAgentDetector.isMissingOrAutomated(userAgent)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/129.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"})
    @DisplayName("counts an ordinary browser")
    void shouldTreatOrdinaryBrowserAsHuman(final String userAgent) {
        assertThat(AutomatedUserAgentDetector.isMissingOrAutomated(userAgent)).isFalse();
    }
}
