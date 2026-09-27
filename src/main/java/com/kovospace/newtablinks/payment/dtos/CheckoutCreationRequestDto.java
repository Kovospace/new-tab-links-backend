package com.kovospace.newtablinks.payment.dtos;

import com.kovospace.newtablinks.payment.models.ProPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * What the signed-in account wants to buy.
 *
 * @param plan the plan; which provider product sells it is server configuration
 * @since 0.0.9
 */
@Schema(description = "What the signed-in account wants to buy")
public record CheckoutCreationRequestDto(

        @Schema(description = "The plan to buy. Which product sells it is decided by the server, "
                + "so the caller never names a product.", example = "LIFETIME")
        @NotNull
        ProPlan plan) {
}
