package com.kovospace.newtablinks.entitlement.models;

import java.util.Objects;

/**
 * An amount a customer was actually charged, in the currency it was charged in.
 *
 * @param amountInMinorUnits the amount in the currency's minor unit - cents for EUR and USD
 * @param currencyCode       three-letter ISO 4217 code, upper case
 * @since 0.0.9
 */
public record ChargedAmount(long amountInMinorUnits, String currencyCode) {

    /**
     * Rejects an amount that could not have been charged.
     *
     * @throws IllegalArgumentException when the amount is negative or the currency is not a
     *                                  three-letter upper-case code
     */
    public ChargedAmount {
        Objects.requireNonNull(currencyCode, "currencyCode");
        if (amountInMinorUnits < 0) {
            throw new IllegalArgumentException("A charged amount cannot be negative");
        }
        if (!currencyCode.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Not an ISO 4217 currency code: " + currencyCode);
        }
    }
}
