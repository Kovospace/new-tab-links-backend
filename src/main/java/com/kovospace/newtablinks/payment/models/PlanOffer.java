package com.kovospace.newtablinks.payment.models;

import java.time.Period;
import java.util.Objects;

/**
 * A plan on sale in one currency, at the price the payment provider reports.
 *
 * @param plan             the plan
 * @param currency         upper-case ISO 4217 code, as the provider reports it
 * @param amountMinorUnits the price in minor units
 * @param billingPeriod    how often it is charged, or {@code null} for a one-time payment
 * @since 0.0.14
 */
public record PlanOffer(ProPlan plan, String currency, long amountMinorUnits, Period billingPeriod) {

    /**
     * Rejects an offer missing its plan or currency.
     *
     * @throws NullPointerException when either is missing
     */
    public PlanOffer {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(currency, "currency");
    }
}
