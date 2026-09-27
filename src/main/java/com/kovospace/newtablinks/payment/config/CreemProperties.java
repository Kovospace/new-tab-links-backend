package com.kovospace.newtablinks.payment.config;

import com.kovospace.newtablinks.payment.models.ProPlan;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials and settings for Creem, bound from {@code newtablinks.payment.creem.*}.
 *
 * <p><strong>Everything is optional, and a missing piece switches its feature off.</strong> With
 * no API key no checkout can be started; with no webhook secret every webhook delivery is refused
 * with 503. Either way the application starts, so local development needs neither.</p>
 *
 * <p><strong>A key/host mismatch is impossible.</strong> The API host is derived from the key's
 * prefix - {@code creem_test_} is the sandbox, any other {@code creem_} key is live. An explicit
 * {@code api-base-url} is accepted only when it names that same host; anything else stops the
 * application at startup rather than failing the first customer's checkout.</p>
 *
 * @param apiKey          secret API key, {@code creem_test_…} or {@code creem_…}; blank disables
 *                        checkout
 * @param webhookSecret   the signing secret of the webhook endpoint registered in the Creem
 *                        dashboard; blank refuses every delivery
 * @param apiBaseUrl      optional; must match the key's mode when set
 * @param apiTimeout      connect and read timeout for calls to Creem
 * @param products        which Creem product each plan sells
 * @param checkout        where the customer returns to after paying
 * @since 0.0.9
 */
@ConfigurationProperties(prefix = "newtablinks.payment.creem")
public record CreemProperties(
        String apiKey,
        String webhookSecret,
        String apiBaseUrl,
        Duration apiTimeout,
        Products products,
        Checkout checkout) {

    /**
     * Rejects a configuration that could not work, at startup rather than per request.
     *
     * @throws IllegalArgumentException when the key is not a Creem key, the host does not match
     *                                  it, or the timeout is not positive
     */
    public CreemProperties {
        apiKey = blankToEmpty(apiKey);
        webhookSecret = blankToEmpty(webhookSecret);
        apiBaseUrl = withoutTrailingSlash(blankToEmpty(apiBaseUrl));
        products = products == null ? new Products(null, null) : products;
        checkout = checkout == null ? new Checkout(null) : checkout;

        if (apiTimeout == null || apiTimeout.isZero() || apiTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "newtablinks.payment.creem.api-timeout must be a positive duration");
        }
        if (!apiKey.isEmpty()) {
            requireHostMatchingKey(resolveModeOf(apiKey), apiBaseUrl);
        }
    }

    /**
     * Tells whether checkouts can be created.
     *
     * @return {@code true} when an API key is configured
     */
    public boolean isApiConfigured() {
        return !apiKey.isEmpty();
    }

    /**
     * Tells whether webhook deliveries can be verified.
     *
     * @return {@code true} when a webhook secret is configured
     */
    public boolean isWebhookConfigured() {
        return !webhookSecret.isEmpty();
    }

    /**
     * Returns the mode of the configured API key.
     *
     * @return the mode, or empty when no key is configured
     */
    public Optional<CreemApiMode> apiMode() {
        return apiKey.isEmpty() ? Optional.empty() : CreemApiMode.ofApiKey(apiKey);
    }

    /**
     * Returns the Creem product that sells a plan.
     *
     * @param plan the plan being bought
     * @return the product identifier, or empty when none is configured for that plan
     */
    public Optional<String> productIdFor(final ProPlan plan) {
        final String productId = switch (plan) {
            case LIFETIME -> products.lifetime();
            case SUBSCRIPTION -> products.subscription();
        };
        return Optional.ofNullable(productId).filter(value -> !value.isBlank());
    }

    /**
     * Keeps both secrets out of anything that prints this object.
     *
     * @return a description naming the mode and whether each secret is present
     */
    @Override
    public String toString() {
        return "CreemProperties[mode=%s, webhookConfigured=%s, products=%s]"
                .formatted(apiMode().orElse(null), isWebhookConfigured(), products);
    }

    /**
     * Determines the mode of a key, refusing anything that is not a Creem key.
     *
     * @param apiKey a non-blank key
     * @return its mode
     */
    private static CreemApiMode resolveModeOf(final String apiKey) {
        return CreemApiMode.ofApiKey(apiKey).orElseThrow(() -> new IllegalArgumentException(
                "CREEM_API_KEY is not a Creem API key: it must start with creem_test_ (test "
                        + "mode) or creem_ (live mode)"));
    }

    /**
     * Stops startup when an explicit host disagrees with the key.
     *
     * @param mode       the key's mode
     * @param apiBaseUrl the configured host, empty when none
     */
    private static void requireHostMatchingKey(final CreemApiMode mode, final String apiBaseUrl) {
        if (!apiBaseUrl.isEmpty() && !apiBaseUrl.equals(mode.apiBaseUrl())) {
            throw new IllegalArgumentException(
                    "CREEM_API_BASE_URL is %s but CREEM_API_KEY is a %s key, which only works "
                            .formatted(apiBaseUrl, mode)
                            + "against " + mode.apiBaseUrl()
                            + ". Leave CREEM_API_BASE_URL unset to derive it from the key.");
        }
    }

    /**
     * Normalises an absent value to the empty string.
     *
     * @param value a configured value, possibly {@code null} or blank
     * @return the trimmed value, or empty
     */
    private static String blankToEmpty(final String value) {
        return value == null ? "" : value.strip();
    }

    /**
     * Drops one trailing slash, so {@code https://api.creem.io/} matches too.
     *
     * @param url a URL, possibly empty
     * @return the URL without a trailing slash
     */
    private static String withoutTrailingSlash(final String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Which Creem product each plan sells.
     *
     * @param lifetime     the one-time product
     * @param subscription the yearly subscription product
     */
    public record Products(String lifetime, String subscription) {
    }

    /**
     * Where a customer goes once a checkout completes.
     *
     * @param successPath path on the website Creem redirects to after payment; blank leaves it to
     *                    the product's own default in the Creem dashboard
     */
    public record Checkout(String successPath) {
    }
}
