package com.kovospace.newtablinks.payment.models;

import java.time.Period;
import java.util.Objects;

/**
 * What the payment provider says a product costs.
 *
 * @param productId        the provider's identifier of the product
 * @param currency         upper-case ISO 4217 code the provider charges in
 * @param amountMinorUnits the price in the currency's minor units (cents), before any tax the
 *                         provider adds at checkout
 * @param billingPeriod    how often it is charged, or {@code null} for a one-time payment
 * @since 0.0.14
 */
public record ProductPrice(
        String productId,
        String currency,
        long amountMinorUnits,
        Period billingPeriod) {

    /**
     * Rejects a price missing what every price needs.
     *
     * @throws NullPointerException when the product or currency is missing
     */
    public ProductPrice {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(currency, "currency");
    }
}
