package com.kovospace.newtablinks.payment.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which currency a visitor is offered first, and how long provider prices are trusted, bound
 * from {@code newtablinks.payment.pricing.*}.
 *
 * <p>Provider-neutral: which product sells a plan in a currency is the provider adapter's
 * configuration ({@link CreemProperties#products()}). This class only decides, from a country,
 * which of the offered currencies to suggest.</p>
 *
 * @param defaultCurrency           suggested for a country with no rule, or no country at all;
 *                                  also the currency of a checkout that names none
 * @param countries                 per currency, the ISO 3166-1 alpha-2 countries it is suggested
 *                                  to ({@code countries.EUR=AT,BE,...}, or the environment variable
 *                                  {@code NEWTABLINKS_PAYMENT_PRICING_COUNTRIES_EUR}); keys and
 *                                  values are case-insensitive, and a country may be listed under
 *                                  one currency only
 * @param priceCacheLifetime        how long a price read from the provider is served before it is
 *                                  read again
 * @param priceRefreshRetryInterval how long to wait after a failed read before trying again;
 *                                  meanwhile the last known prices are served
 * @since 0.0.14
 */
@ConfigurationProperties(prefix = "newtablinks.payment.pricing")
public record PaymentPricingProperties(
        String defaultCurrency,
        Map<String, List<String>> countries,
        Duration priceCacheLifetime,
        Duration priceRefreshRetryInterval) {

    /** An ISO 4217 alphabetic code, once upper-cased. */
    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

    /** An ISO 3166-1 alpha-2 code, once upper-cased. */
    private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{2}");

    /**
     * Normalises every code to upper case and rejects a configuration that could not work.
     *
     * @throws IllegalArgumentException when a code is malformed, a country is listed under two
     *                                  currencies, or a duration is not positive
     */
    public PaymentPricingProperties {
        defaultCurrency = requireCurrencyCode(defaultCurrency, "default-currency");
        countries = normaliseCountriesByCurrency(countries);
        requirePositive(priceCacheLifetime, "price-cache-lifetime");
        requirePositive(priceRefreshRetryInterval, "price-refresh-retry-interval");
    }

    /**
     * Returns the currency a country is configured to be offered first.
     *
     * @param countryCode an ISO 3166-1 alpha-2 code in any case; {@code null}, blank, or a code
     *                    no rule lists (Cloudflare's {@code XX} and {@code T1} among them) all
     *                    find nothing
     * @return the upper-case currency code, or empty when no rule covers the country
     */
    public Optional<String> currencyForCountry(final String countryCode) {
        if (countryCode == null) {
            return Optional.empty();
        }
        final String normalisedCountry = countryCode.strip().toUpperCase(Locale.ROOT);
        return countries.entrySet().stream()
                .filter(entry -> entry.getValue().contains(normalisedCountry))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Upper-cases every code and checks no country belongs to two currencies.
     *
     * @param configured the bound map, possibly {@code null}
     * @return an unmodifiable map of upper-case currency to upper-case countries
     */
    private static Map<String, List<String>> normaliseCountriesByCurrency(
            final Map<String, List<String>> configured) {

        if (configured == null) {
            return Map.of();
        }
        final Map<String, List<String>> normalised = new HashMap<>();
        final Map<String, String> currencyOfCountry = new HashMap<>();
        configured.forEach((configuredCurrency, configuredCountries) -> {
            final String currency = requireCurrencyCode(configuredCurrency,
                    "countries." + configuredCurrency);
            final List<String> countries = normaliseCountries(configuredCountries, currency);
            countries.forEach(country -> requireSingleCurrency(currencyOfCountry, country, currency));
            normalised.put(currency, countries);
        });
        return Map.copyOf(normalised);
    }

    /**
     * Upper-cases and checks one currency's country list.
     *
     * @param configuredCountries the bound list, possibly {@code null}
     * @param currency            the currency it belongs to, for the error message
     * @return the upper-case, non-blank codes
     */
    private static List<String> normaliseCountries(
            final List<String> configuredCountries, final String currency) {

        if (configuredCountries == null) {
            return List.of();
        }
        return configuredCountries.stream()
                .map(country -> country == null ? "" : country.strip().toUpperCase(Locale.ROOT))
                .filter(country -> !country.isEmpty())
                .map(country -> requireCountryCode(country, currency))
                .distinct()
                .toList();
    }

    /**
     * Checks the shape of an upper-cased country code.
     *
     * @param country  the code
     * @param currency the currency listing it, for the error message
     * @return the code, unchanged
     */
    private static String requireCountryCode(final String country, final String currency) {
        if (!COUNTRY_CODE.matcher(country).matches()) {
            throw new IllegalArgumentException("newtablinks.payment.pricing.countries."
                    + currency + " lists " + country + ", which is not a two-letter country code");
        }
        return country;
    }

    /**
     * Records which currency a country belongs to, refusing a second one.
     *
     * @param currencyOfCountry the assignments made so far
     * @param country           the country
     * @param currency          the currency claiming it
     */
    private static void requireSingleCurrency(
            final Map<String, String> currencyOfCountry, final String country, final String currency) {

        final String earlierCurrency = currencyOfCountry.putIfAbsent(country, currency);
        if (earlierCurrency != null) {
            throw new IllegalArgumentException("newtablinks.payment.pricing.countries "
                    + "lists " + country + " under both " + earlierCurrency + " and " + currency);
        }
    }

    /**
     * Upper-cases a currency code and checks its shape.
     *
     * @param configured   the configured value
     * @param propertyName the property it came from, for the error message
     * @return the upper-case code
     */
    private static String requireCurrencyCode(final String configured, final String propertyName) {
        final String currency = configured == null ? "" : configured.strip().toUpperCase(Locale.ROOT);
        if (!CURRENCY_CODE.matcher(currency).matches()) {
            throw new IllegalArgumentException("newtablinks.payment.pricing." + propertyName
                    + " must be a three-letter ISO 4217 currency code");
        }
        return currency;
    }

    /**
     * Refuses a missing, zero or negative duration.
     *
     * @param duration     the configured duration
     * @param propertyName the property it came from, for the error message
     */
    private static void requirePositive(final Duration duration, final String propertyName) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(
                    "newtablinks.payment.pricing." + propertyName + " must be a positive duration");
        }
    }
}
