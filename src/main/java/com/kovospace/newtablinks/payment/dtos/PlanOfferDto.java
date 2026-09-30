package com.kovospace.newtablinks.payment.dtos;

import com.kovospace.newtablinks.payment.models.ProPlan;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One plan on sale in one currency.
 *
 * @param plan             the plan, as {@code POST /api/v1/payments/checkouts} takes it
 * @param currency         ISO 4217 code the payment provider charges in
 * @param amountMinorUnits the price in the currency's minor units
 * @param billingPeriod    ISO 8601 period between charges, {@code null} for a one-time payment
 * @since 0.0.14
 */
@Schema(description = "One plan on sale in one currency")
public record PlanOfferDto(

        @Schema(description = "The plan, as the checkout endpoint takes it", example = "SUBSCRIPTION")
        ProPlan plan,

        @Schema(description = "ISO 4217 code of the currency, as the payment provider charges it; "
                + "pass it as `currency` to the checkout endpoint", example = "EUR")
        String currency,

        @Schema(description = "Price in the currency's minor units (cents), before any tax the "
                + "payment provider adds at checkout", example = "468")
        long amountMinorUnits,

        @Schema(description = "ISO 8601 period between charges; null for a one-time payment",
                example = "P1Y", nullable = true)
        String billingPeriod) {
}
