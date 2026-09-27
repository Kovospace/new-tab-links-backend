package com.kovospace.newtablinks.payment.config;

import java.util.Optional;

/**
 * Which of Creem's two isolated environments an API key belongs to.
 *
 * <p>Creem decides this by the key itself: test keys start with {@code creem_test_}, live keys
 * with {@code creem_}, and each works only against its own host. Deriving the host from the key
 * is therefore the only way to make a key/host mismatch impossible rather than merely unlikely.</p>
 *
 * @since 0.0.9
 */
public enum CreemApiMode {

    /** The sandbox: simulated payments with test cards, no real money. */
    TEST("creem_test_", "https://test-api.creem.io"),

    /** Real payments. */
    LIVE("creem_", "https://api.creem.io");

    private final String apiKeyPrefix;
    private final String apiBaseUrl;

    /**
     * Declares a mode.
     *
     * @param apiKeyPrefix what every API key of this mode starts with
     * @param apiBaseUrl   the only host such a key works against
     */
    CreemApiMode(final String apiKeyPrefix, final String apiBaseUrl) {
        this.apiKeyPrefix = apiKeyPrefix;
        this.apiBaseUrl = apiBaseUrl;
    }

    /**
     * Tells which mode an API key belongs to.
     *
     * <p>The test prefix is checked first because it is the longer one: every test key also
     * starts with the live prefix.</p>
     *
     * @param apiKey a Creem API key
     * @return the mode, or empty when the value is not a Creem API key at all
     */
    public static Optional<CreemApiMode> ofApiKey(final String apiKey) {
        if (apiKey.startsWith(TEST.apiKeyPrefix)) {
            return Optional.of(TEST);
        }
        if (apiKey.startsWith(LIVE.apiKeyPrefix)) {
            return Optional.of(LIVE);
        }
        return Optional.empty();
    }

    /**
     * Returns the only host an API key of this mode works against.
     *
     * @return the base URL, without a trailing slash and without the {@code /v1} version path
     */
    public String apiBaseUrl() {
        return apiBaseUrl;
    }
}
