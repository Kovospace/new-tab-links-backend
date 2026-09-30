package com.kovospace.newtablinks.payment.models;

import java.util.List;
import java.util.Objects;

/**
 * Everything on sale, and the currency to show first.
 *
 * @param suggestedCurrency upper-case ISO 4217 code suggested for the visitor; always one of the
 *                          offers' currencies when there are any offers
 * @param offers            every plan on sale in every currency whose price is known
 * @since 0.0.14
 */
public record PaymentOffers(String suggestedCurrency, List<PlanOffer> offers) {

    /**
     * Copies the offers, so the record stays immutable.
     *
     * @throws NullPointerException when the suggestion or the list is missing
     */
    public PaymentOffers {
        Objects.requireNonNull(suggestedCurrency, "suggestedCurrency");
        offers = List.copyOf(offers);
    }
}
