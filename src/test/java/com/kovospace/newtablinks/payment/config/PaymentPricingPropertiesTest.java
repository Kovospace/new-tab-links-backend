package com.kovospace.newtablinks.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the country-to-currency rules: normalisation, the codes Cloudflare uses for "unknown",
 * and the configurations refused at startup.
 *
 * @since 0.0.14
 */
class PaymentPricingPropertiesTest {

    private static final PaymentPricingProperties PRICING = pricingWith(
            "usd", Map.of("eur", List.of("sk", " DE ", "AT")));

    @Test
    @DisplayName("finds a listed country's currency in any case")
    void shouldFindTheCurrencyOfAListedCountry() {
        assertThat(PRICING.defaultCurrency()).isEqualTo("USD");
        assertThat(PRICING.currencyForCountry("SK")).contains("EUR");
        assertThat(PRICING.currencyForCountry("de")).contains("EUR");
    }

    @Test
    @DisplayName("finds no rule for a missing header, XX, T1 or an unlisted country")
    void shouldFindNoRuleForUnknownCountries() {
        assertThat(PRICING.currencyForCountry(null)).isEmpty();
        assertThat(PRICING.currencyForCountry("")).isEmpty();
        assertThat(PRICING.currencyForCountry("XX")).isEmpty();
        assertThat(PRICING.currencyForCountry("T1")).isEmpty();
        assertThat(PRICING.currencyForCountry("US")).isEmpty();
    }

    @Test
    @DisplayName("refuses a country listed under two currencies")
    void shouldRefuseACountryUnderTwoCurrencies() {
        assertThatThrownBy(() -> pricingWith("USD",
                Map.of("EUR", List.of("SK"), "CZK", List.of("CZ", "SK"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SK");
    }

    @Test
    @DisplayName("refuses malformed currency and country codes")
    void shouldRefuseMalformedCodes() {
        assertThatThrownBy(() -> pricingWith("DOLLAR", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default-currency");
        assertThatThrownBy(() -> pricingWith("USD", Map.of("EUR", List.of("SVK"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SVK");
    }

    /**
     * Builds validated pricing properties with working durations.
     *
     * @param defaultCurrency   the default currency
     * @param currencyCountries the country rules
     * @return the properties
     */
    private static PaymentPricingProperties pricingWith(
            final String defaultCurrency, final Map<String, List<String>> currencyCountries) {

        return new PaymentPricingProperties(defaultCurrency, currencyCountries,
                Duration.ofHours(1), Duration.ofMinutes(5));
    }
}
