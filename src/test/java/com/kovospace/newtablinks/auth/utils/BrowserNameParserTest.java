package com.kovospace.newtablinks.auth.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies that browsers are told apart despite all claiming to be each other.
 *
 * <p>The cases below are real user agent strings, and every one of them contains the marker of at
 * least one browser it is not - which is exactly why the parser's check order matters.</p>
 *
 * @since 0.0.3
 */
class BrowserNameParserTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
            // Edge says Chrome and Safari too.
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0 | Edge",
            // Opera says Chrome and Safari too.
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 OPR/115.0.0.0 | Opera",
            // Vivaldi says Chrome and Safari too.
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Vivaldi/7.0.3495.11 | Vivaldi",
            // Plain Chrome still says Safari.
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 | Chrome",
            "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0 | Firefox",
            // Firefox on iOS is not called Firefox.
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) FxiOS/130.0 Mobile/15E148 Safari/605.1.15 | Firefox",
            // Genuine Safari: the only one here with no other browser's marker.
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15 | Safari",
    })
    @DisplayName("a browser is named despite impersonating the others")
    void shouldNameTheBrowserDespiteImpersonation(final String userAgent, final String expected) {
        assertThat(BrowserNameParser.parseBrowserName(userAgent)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "curl/8.5.0", "something entirely unrecognisable"})
    @DisplayName("anything unrecognised is labelled rather than guessed at")
    void shouldFallBackToUnknownForAnythingElse(final String userAgent) {
        assertThat(BrowserNameParser.parseBrowserName(userAgent))
                .isEqualTo(BrowserNameParser.UNKNOWN_BROWSER);
    }

    @Test
    @DisplayName("an absurdly long header cannot be used to stuff the column")
    void shouldNotBeDerailedByAnOverlongHeader() {
        final String padded = "x".repeat(10_000) + "Firefox/130.0";
        assertThat(BrowserNameParser.parseBrowserName(padded))
                .isEqualTo(BrowserNameParser.UNKNOWN_BROWSER);
    }
}
