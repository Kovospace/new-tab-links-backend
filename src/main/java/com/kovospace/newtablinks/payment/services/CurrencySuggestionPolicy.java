package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.payment.config.PaymentPricingProperties;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Decides which currency to show a visitor first, from the country their request came from.
 *
 * <p>Only ever a suggestion: the visitor can switch, and the country is whatever the edge proxy
 * claims, which a caller bypassing it can make up. Nothing is stored or logged about it.</p>
 *
 * <p>In order: the currency the country's rule names, when it is on sale; otherwise the default
 * currency, when it is on sale; otherwise the alphabetically first currency that is; and with
 * nothing on sale at all, the default currency.</p>
 *
 * @since 0.0.14
 */
@Component
public class CurrencySuggestionPolicy {

    private final PaymentPricingProperties paymentPricingProperties;

    /**
     * Creates the policy.
     *
     * @param paymentPricingProperties the default currency and the per-currency countries
     */
    public CurrencySuggestionPolicy(final PaymentPricingProperties paymentPricingProperties) {
        this.paymentPricingProperties = paymentPricingProperties;
    }

    /**
     * Picks the currency to suggest.
     *
     * @param countryCode        ISO 3166-1 alpha-2 code of the visitor's country, or {@code null}
     *                           when unknown; codes no rule lists fall back to the default
     * @param offeredCurrencies  upper-case codes of every currency with at least one offer
     * @return an upper-case currency code; one of {@code offeredCurrencies} unless it is empty
     */
    public String suggestCurrency(final String countryCode, final Set<String> offeredCurrencies) {
        final String defaultCurrency = paymentPricingProperties.defaultCurrency();
        final String countryCurrency = paymentPricingProperties.currencyForCountry(countryCode)
                .orElse(defaultCurrency);
        if (offeredCurrencies.contains(countryCurrency)) {
            return countryCurrency;
        }
        if (offeredCurrencies.contains(defaultCurrency)) {
            return defaultCurrency;
        }
        return offeredCurrencies.stream().sorted().findFirst().orElse(defaultCurrency);
    }
}
