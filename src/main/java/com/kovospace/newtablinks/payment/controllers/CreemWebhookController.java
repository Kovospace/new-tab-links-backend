package com.kovospace.newtablinks.payment.controllers;

import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.payment.dtos.PaymentWebhookReceiptDto;
import com.kovospace.newtablinks.payment.services.CreemWebhookInterpreter;
import com.kovospace.newtablinks.payment.services.PaymentWebhookProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Creem's webhook deliveries.
 *
 * <p>Public by necessity - Creem has no token of ours - and exempted from authentication in the
 * security configuration explicitly; without that exemption the catch-all
 * {@code anyRequest().authenticated()} answers every delivery with 401, Creem gives up after five
 * attempts, and the first sign is a customer who paid and got nothing. The signature is the
 * authentication.</p>
 *
 * <p><strong>The body is bound as {@code byte[]}, never {@code String}.</strong> Creem sends no
 * charset, so a {@code String} binding would be decoded as ISO-8859-1 and every non-ASCII byte
 * would reach the HMAC changed: the signature would verify for most customers and fail for the
 * one with an accent in their name.</p>
 *
 * @since 0.0.9
 */
@RestController
@Tag(name = "Payments", description = "Buying pro, and the provider's notifications about it")
public class CreemWebhookController {

    private final PaymentWebhookProcessingService paymentWebhookProcessingService;

    /**
     * Creates the controller.
     *
     * @param paymentWebhookProcessingService verifies, claims and applies a delivery
     */
    public CreemWebhookController(
            final PaymentWebhookProcessingService paymentWebhookProcessingService) {

        this.paymentWebhookProcessingService = paymentWebhookProcessingService;
    }

    /**
     * Accepts one webhook delivery.
     *
     * @param requestBody the body exactly as Creem sent it; {@code null} when there was none
     * @param signature   the {@code creem-signature} header, possibly absent
     * @return the acknowledgement
     */
    @PostMapping(ApiEndpointPaths.CREEM_WEBHOOK_PATH)
    @Operation(summary = "Receive a Creem webhook delivery",
            description = "Called by Creem, not by any client of this API. Authenticated by the "
                    + "HMAC-SHA256 of the raw body in the `creem-signature` header. Idempotent: a "
                    + "repeated delivery of the same event is acknowledged with 200 and changes "
                    + "nothing. Events older than the newest one applied to an account are "
                    + "acknowledged and refused.")
    @ApiResponse(responseCode = "200",
            description = "Processed, repeated, or of a type this service does not act on")
    @ApiResponse(responseCode = "400", description = "Correctly signed, but not readable",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "401", description = "Signature missing or wrong",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "409",
            description = "An earlier delivery of this event is still being processed; retry",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "503", description = "Webhooks are not configured on this server",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public PaymentWebhookReceiptDto receiveCreemWebhook(
            @RequestBody(required = false) final byte[] requestBody,
            @RequestHeader(name = CreemWebhookInterpreter.SIGNATURE_HEADER, required = false)
            final String signature) {

        return paymentWebhookProcessingService.processDelivery(requestBody, signature);
    }
}
