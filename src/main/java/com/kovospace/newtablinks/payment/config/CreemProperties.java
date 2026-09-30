package com.kovospace.newtablinks.payment.config;

import com.kovospace.newtablinks.payment.models.CatalogProduct;
import com.kovospace.newtablinks.payment.models.ProPlan;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
 * @param products        which Creem product sells each plan, per ISO 4217 currency code; a
 *                        currency with no product for a plan does not sell that plan, and one
 *                        with neither is not offered at all. Keys are case-insensitive, so
 *                        {@code products.EUR.lifetime} and the environment variable
 *                        {@code NEWTABLINKS_PAYMENT_CREEM_PRODUCTS_EUR_LIFETIME} name the same
 *                        entry
 * @param checkout        where the customer returns to after paying
 * @since 0.0.9
 */
@ConfigurationProperties(prefix = "newtablinks.payment.creem")
public record CreemProperties(
        String apiKey,
        String webhookSecret,
        String apiBaseUrl,
        Duration apiTimeout,
        Map<String, Products> products,
        Checkout checkout) {

    /** What a catalog key must look like once upper-cased: an ISO 4217 alphabetic code. */
    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

    /**
     * Rejects a configuration that could not work, at startup rather than per request.
     *
     * @throws IllegalArgumentException when the key is not a Creem key, the host does not match
     *                                  it, the timeout is not positive, or a catalog key is not a
     *                                  three-letter currency code
     */
    public CreemProperties {
        apiKey = blankToEmpty(apiKey);
        webhookSecret = blankToEmpty(webhookSecret);
        apiBaseUrl = withoutTrailingSlash(blankToEmpty(apiBaseUrl));
        products = normaliseCatalog(products);
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
     * Returns the Creem product that sells a plan in a currency.
     *
     * @param plan         the plan being bought
     * @param currencyCode an ISO 4217 code, in any case
     * @return the product identifier, or empty when that currency does not sell that plan
     * @since 0.0.14
     */
    public Optional<String> productIdFor(final ProPlan plan, final String currencyCode) {
        if (currencyCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(products.get(currencyCode.strip().toUpperCase(Locale.ROOT)))
                .flatMap(productsOfCurrency -> productsOfCurrency.productIdFor(plan));
    }

    /**
     * Lists every configured product, the whole catalog flattened.
     *
     * @return one entry per currency and plan that has a product, ordered by currency and then
     *         by plan; empty when nothing is on sale
     * @since 0.0.14
     */
    public List<CatalogProduct> catalogProducts() {
        return products.entrySet().stream()
                .flatMap(entry -> catalogProductsOf(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CatalogProduct::currency)
                        .thenComparing(CatalogProduct::plan))
                .toList();
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
     * Flattens one currency's products into catalog entries.
     *
     * @param currencyCode      the upper-case currency code
     * @param productsOfCurrency that currency's products
     * @return one entry per plan that has a product
     */
    private static Stream<CatalogProduct> catalogProductsOf(
            final String currencyCode, final Products productsOfCurrency) {

        return Stream.of(ProPlan.values())
                .flatMap(plan -> productsOfCurrency.productIdFor(plan).stream()
                        .map(productId -> new CatalogProduct(currencyCode, plan, productId)));
    }

    /**
     * Upper-cases the catalog's currency keys and drops currencies with no product at all.
     *
     * <p>Keys are upper-cased because relaxed binding lower-cases a key that arrives through an
     * environment variable, while a properties file keeps whatever case it was written in.</p>
     *
     * @param configuredProducts the bound map, possibly {@code null}
     * @return an unmodifiable map ordered by currency code
     * @throws IllegalArgumentException when a key is not a three-letter code, or two keys differ
     *                                  only in case
     */
    private static Map<String, Products> normaliseCatalog(
            final Map<String, Products> configuredProducts) {

        final Map<String, Products> catalog = new TreeMap<>();
        if (configuredProducts == null) {
            return Map.of();
        }
        configuredProducts.forEach((configuredCode, productsOfCurrency) -> {
            final String currencyCode = requireCurrencyCode(configuredCode);
            if (productsOfCurrency != null && productsOfCurrency.sellsAnything()
                    && catalog.put(currencyCode, productsOfCurrency) != null) {
                throw new IllegalArgumentException(
                        "newtablinks.payment.creem.products lists " + currencyCode + " twice");
            }
        });
        return Map.copyOf(catalog);
    }

    /**
     * Upper-cases a catalog key and checks it is a currency code.
     *
     * @param configuredCode the key as bound
     * @return the upper-case code
     * @throws IllegalArgumentException when it is not three letters
     */
    private static String requireCurrencyCode(final String configuredCode) {
        final String currencyCode = blankToEmpty(configuredCode).toUpperCase(Locale.ROOT);
        if (!CURRENCY_CODE.matcher(currencyCode).matches()) {
            throw new IllegalArgumentException("newtablinks.payment.creem.products." + configuredCode
                    + " is not keyed by a three-letter ISO 4217 currency code");
        }
        return currencyCode;
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
     * Which Creem product sells each plan, in one currency.
     *
     * @param lifetime     the one-time product, blank when the plan is not sold in this currency
     * @param subscription the yearly subscription product, blank likewise
     */
    public record Products(String lifetime, String subscription) {

        /**
         * Returns the product selling a plan.
         *
         * @param plan the plan
         * @return the product identifier, or empty when it is blank
         */
        public Optional<String> productIdFor(final ProPlan plan) {
            final String productId = switch (plan) {
                case LIFETIME -> lifetime;
                case SUBSCRIPTION -> subscription;
            };
            return Optional.ofNullable(productId).map(String::strip).filter(value -> !value.isEmpty());
        }

        /**
         * Tells whether this currency sells any plan at all.
         *
         * @return {@code true} when at least one product is configured
         */
        boolean sellsAnything() {
            return Stream.of(ProPlan.values()).anyMatch(plan -> productIdFor(plan).isPresent());
        }
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
