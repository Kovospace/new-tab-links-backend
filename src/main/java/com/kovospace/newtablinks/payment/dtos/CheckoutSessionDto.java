package com.kovospace.newtablinks.payment.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A checkout the customer can now pay at.
 *
 * @param checkoutUrl where to send the customer's browser
 * @since 0.0.9
 */
@Schema(description = "A checkout the customer can now pay at")
public record CheckoutSessionDto(

        @Schema(description = "Where to send the customer's browser. The page is the payment "
                + "provider's; the account becomes pro when its webhook arrives, not on return.",
                example = "https://checkout.creem.io/ch_1QyIQDw9cbFWdA1ry5Qc6I")
        String checkoutUrl) {
}
