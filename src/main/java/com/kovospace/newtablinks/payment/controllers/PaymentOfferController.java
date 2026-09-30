package com.kovospace.newtablinks.payment.controllers;

import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.payment.dtos.PaymentOffersDto;
import com.kovospace.newtablinks.payment.services.PaymentOfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public price list: what can be bought, in which currencies, at what price.
 *
 * @since 0.0.14
 */
@RestController
@Tag(name = "Payments", description = "Buying pro, and the provider's notifications about it")
public class PaymentOfferController {

    /**
     * The header Cloudflare adds to every proxied request, naming the ISO 3166-1 alpha-2 country
     * of the client's address - {@code XX} when unknown, {@code T1} for Tor.
     */
    static final String CLOUDFLARE_COUNTRY_HEADER = "CF-IPCountry";

    private final PaymentOfferService paymentOfferService;

    /**
     * Creates the controller.
     *
     * @param paymentOfferService lists the offers and picks the suggested currency
     */
    public PaymentOfferController(final PaymentOfferService paymentOfferService) {
        this.paymentOfferService = paymentOfferService;
    }

    /**
     * Lists every plan on sale, and the currency to show the visitor first.
     *
     * @param countryCode the edge proxy's idea of the caller's country, or {@code null}; read for
     *                    this one answer, never stored or logged
     * @return the offers and the suggestion
     */
    @GetMapping(ApiEndpointPaths.PAYMENT_OFFERS_PATH)
    @Operation(summary = "List the plans on sale, per currency",
            description = "Public: no access token is needed, and one that is sent is ignored. "
                    + "Prices are the payment provider's own, cached by the server, so calling "
                    + "this often never reaches the provider. An offer whose price has never "
                    + "been read is left out, and with payments not configured the list is "
                    + "empty. `suggestedCurrency` comes from the country the edge proxy "
                    + "attributes the request to (Cloudflare's CF-IPCountry header); it is only a "
                    + "suggestion, and the visitor may check out in any listed currency.")
    @ApiResponse(responseCode = "200", description = "The offers, possibly none")
    public PaymentOffersDto listOffers(
            @Parameter(in = ParameterIn.HEADER, name = CLOUDFLARE_COUNTRY_HEADER,
                    description = "Set by Cloudflare, not by the client: ISO 3166-1 alpha-2 "
                            + "country of the caller's address. Absent, XX, T1 or a country "
                            + "without a rule means the default currency.", example = "SK")
            @RequestHeader(name = CLOUDFLARE_COUNTRY_HEADER, required = false)
            final String countryCode) {

        return paymentOfferService.describeOffersFor(countryCode);
    }
}
