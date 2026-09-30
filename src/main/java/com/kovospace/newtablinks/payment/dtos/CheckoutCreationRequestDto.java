package com.kovospace.newtablinks.payment.dtos;

import com.kovospace.newtablinks.payment.models.ProPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * What the signed-in account wants to buy, and in which currency.
 *
 * @param plan     the plan; which provider product sells it is server configuration
 * @param currency ISO 4217 code to pay in, case-insensitive; {@code null} means the server's
 *                 default currency
 * @since 0.0.9
 */
@Schema(description = "What the signed-in account wants to buy")
public record CheckoutCreationRequestDto(

        @Schema(description = "The plan to buy. Which product sells it is decided by the server, "
                + "so the caller never names a product.", example = "LIFETIME")
        @NotNull
        ProPlan plan,

        @Schema(description = "ISO 4217 code of the currency to pay in, one of the currencies "
                + "GET /api/v1/payments/offers lists for this plan. Omitted means the server's "
                + "default currency. A currency that does not sell the plan is refused with 400, "
                + "naming this field.", example = "EUR", nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Pattern(regexp = "[A-Za-z]{3}", message = "must be a three-letter ISO 4217 currency code")
        String currency) {
}
