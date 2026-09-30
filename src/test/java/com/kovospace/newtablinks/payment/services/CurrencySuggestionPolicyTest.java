package com.kovospace.newtablinks.payment.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.kovospace.newtablinks.payment.config.PaymentPricingProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests which currency a visitor is shown first, and every fallback when it is not on sale.
 *
 * @since 0.0.14
 */
class CurrencySuggestionPolicyTest {

    private static final Set<String> EUR_AND_USD = Set.of("EUR", "USD");

    private final CurrencySuggestionPolicy policy = new CurrencySuggestionPolicy(
            new PaymentPricingProperties("USD", Map.of("EUR", List.of("SK", "DE", "XK")),
                    Duration.ofHours(1), Duration.ofMinutes(5)));

    @Test
    @DisplayName("suggests EUR to a euro country and USD to one without a rule")
    void shouldSuggestTheCountrysCurrency() {
        assertThat(policy.suggestCurrency("SK", EUR_AND_USD)).isEqualTo("EUR");
        assertThat(policy.suggestCurrency("xk", EUR_AND_USD)).isEqualTo("EUR");
        assertThat(policy.suggestCurrency("US", EUR_AND_USD)).isEqualTo("USD");
        assertThat(policy.suggestCurrency("JP", EUR_AND_USD)).isEqualTo("USD");
    }

    @Test
    @DisplayName("suggests the default for a missing header, XX and T1")
    void shouldSuggestTheDefaultWhenTheCountryIsUnknown() {
        assertThat(policy.suggestCurrency(null, EUR_AND_USD)).isEqualTo("USD");
        assertThat(policy.suggestCurrency("XX", EUR_AND_USD)).isEqualTo("USD");
        assertThat(policy.suggestCurrency("T1", EUR_AND_USD)).isEqualTo("USD");
    }

    @Test
    @DisplayName("falls back to the default, then to any offered currency, when the rule's is "
            + "not on sale")
    void shouldFallBackWhenTheSuggestedCurrencyIsNotOffered() {
        assertThat(policy.suggestCurrency("SK", Set.of("USD"))).isEqualTo("USD");
        assertThat(policy.suggestCurrency("US", Set.of("EUR"))).isEqualTo("EUR");
        assertThat(policy.suggestCurrency("US", Set.of("GBP", "CZK"))).isEqualTo("CZK");
    }

    @Test
    @DisplayName("suggests the default when nothing is on sale")
    void shouldSuggestTheDefaultWhenNothingIsOffered() {
        assertThat(policy.suggestCurrency("SK", Set.of())).isEqualTo("USD");
    }
}
