package com.kovospace.newtablinks.payment.controllers;

import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.payment.dtos.CheckoutCreationRequestDto;
import com.kovospace.newtablinks.payment.dtos.CheckoutSessionDto;
import com.kovospace.newtablinks.payment.services.PaymentCheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts a purchase for the signed-in account.
 *
 * @since 0.0.9
 */
@RestController
@RequestMapping("/api/v1/payments/checkouts")
@Tag(name = "Payments", description = "Buying pro, and the provider's notifications about it")
public class PaymentCheckoutController {

    private final PaymentCheckoutService paymentCheckoutService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    /**
     * Creates the controller.
     *
     * @param paymentCheckoutService    opens the checkout at the provider
     * @param authenticatedUserProvider identifies the paying account from its access token
     */
    public PaymentCheckoutController(
            final PaymentCheckoutService paymentCheckoutService,
            final AuthenticatedUserProvider authenticatedUserProvider) {

        this.paymentCheckoutService = paymentCheckoutService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    /**
     * Opens a checkout for the signed-in account.
     *
     * @param checkoutCreationRequest what to buy
     * @return where to send the customer to pay
     */
    @PostMapping
    @Operation(summary = "Open a checkout for the signed-in account",
            description = "Returns the payment provider's checkout page for the chosen plan, in "
                    + "the chosen currency or, when none is given, the server's default. The "
                    + "account is carried through the checkout from the access token, never from "
                    + "the request, so a payment is always attributed to whoever started it. The "
                    + "account becomes pro when the provider's webhook arrives, not when the "
                    + "customer returns to the site.")
    @ApiResponse(responseCode = "200", description = "The checkout is open")
    @ApiResponse(responseCode = "400", description = "No plan, an unknown one, a malformed "
            + "currency, or a currency that does not sell the plan (reported against the "
            + "`currency` field)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "502", description = "The payment provider refused or failed",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "503",
            description = "Payments are not configured on this server",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public CheckoutSessionDto openCheckout(
            @Valid @RequestBody final CheckoutCreationRequestDto checkoutCreationRequest) {

        return paymentCheckoutService.startCheckout(
                authenticatedUserProvider.getAuthenticatedUserId(),
                checkoutCreationRequest.plan(),
                checkoutCreationRequest.currency());
    }
}
