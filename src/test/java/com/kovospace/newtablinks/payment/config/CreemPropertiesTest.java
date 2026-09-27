package com.kovospace.newtablinks.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kovospace.newtablinks.payment.models.ProPlan;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that a Creem key and host can never disagree, and that nothing configured still starts.
 *
 * @since 0.0.9
 */
class CreemPropertiesTest {

    private static final String TEST_KEY = "creem_test_2B9wYk1bXQ3xR8";
    private static final String LIVE_KEY = "creem_7Hq2Lm0dNp4sVt";

    @Test
    @DisplayName("starts with nothing configured, with both features switched off")
    void shouldStartWithNothingConfigured() {
        final CreemProperties properties = propertiesWith(null, null);

        assertThat(properties.isApiConfigured()).isFalse();
        assertThat(properties.isWebhookConfigured()).isFalse();
        assertThat(properties.apiMode()).isEmpty();
        assertThat(properties.productIdFor(ProPlan.LIFETIME)).isEmpty();
    }

    @Test
    @DisplayName("derives the sandbox host from a test key and the live host from a live key")
    void shouldDeriveTheHostFromTheKey() {
        assertThat(propertiesWith(TEST_KEY, null).apiMode()).contains(CreemApiMode.TEST);
        assertThat(CreemApiMode.TEST.apiBaseUrl()).isEqualTo("https://test-api.creem.io");
        assertThat(propertiesWith(LIVE_KEY, null).apiMode()).contains(CreemApiMode.LIVE);
        assertThat(CreemApiMode.LIVE.apiBaseUrl()).isEqualTo("https://api.creem.io");
    }

    @Test
    @DisplayName("accepts an explicit host that matches the key, trailing slash or not")
    void shouldAcceptAMatchingExplicitHost() {
        assertThat(propertiesWith(TEST_KEY, "https://test-api.creem.io/").apiMode())
                .contains(CreemApiMode.TEST);
    }

    @Test
    @DisplayName("refuses to start with a test key pointed at the live host, and the reverse")
    void shouldRefuseAMismatchedHost() {
        assertThatThrownBy(() -> propertiesWith(TEST_KEY, "https://api.creem.io"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CREEM_API_BASE_URL");
        assertThatThrownBy(() -> propertiesWith(LIVE_KEY, "https://test-api.creem.io"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("refuses to start with something that is not a Creem key")
    void shouldRefuseAForeignKey() {
        assertThatThrownBy(() -> propertiesWith("sk_test_stripe_key", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CREEM_API_KEY");
    }

    @Test
    @DisplayName("never prints either secret")
    void shouldKeepSecretsOutOfToString() {
        final CreemProperties properties = new CreemProperties(TEST_KEY, "whsec_secret_value", null,
                Duration.ofSeconds(5), null, null);

        assertThat(properties.toString()).doesNotContain(TEST_KEY).doesNotContain("whsec_secret_value");
    }

    /**
     * Builds properties with a key and an explicit host.
     *
     * @param apiKey     the key, or {@code null}
     * @param apiBaseUrl the host, or {@code null}
     * @return the validated properties
     */
    private static CreemProperties propertiesWith(final String apiKey, final String apiBaseUrl) {
        return new CreemProperties(apiKey, null, apiBaseUrl, Duration.ofSeconds(5), null, null);
    }
}
