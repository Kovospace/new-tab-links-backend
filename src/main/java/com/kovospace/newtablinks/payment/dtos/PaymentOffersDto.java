package com.kovospace.newtablinks.payment.dtos;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Everything on sale, and which currency to show the visitor first.
 *
 * @param suggestedCurrency ISO 4217 code suggested from the visitor's country
 * @param offers            every plan on sale in every currency whose price is known
 * @since 0.0.14
 */
@Schema(description = "Everything on sale, and which currency to show the visitor first")
public record PaymentOffersDto(

        @Schema(description = "ISO 4217 code suggested from the country the request came from. "
                + "One of the offers' currencies whenever there are offers; the server's default "
                + "currency when there are none.", example = "USD")
        String suggestedCurrency,

        @ArraySchema(arraySchema = @Schema(description = "Every plan on sale in every currency "
                + "whose price is known, grouped by currency. Empty when payments are not "
                + "configured or no price could be read yet."))
        List<PlanOfferDto> offers) {
}
