package com.kovospace.newtablinks.payment.dtos;

import com.kovospace.newtablinks.payment.models.PaymentWebhookOutcome;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The acknowledgement of one webhook delivery.
 *
 * <p>The provider only reads the status code; the body is for whoever looks at the delivery log
 * in the provider's dashboard.</p>
 *
 * @param eventId   the provider's identifier of the event
 * @param duplicate whether this delivery repeated one already processed
 * @param outcome   what processing did; {@code null} for a duplicate
 * @since 0.0.9
 */
@Schema(description = "Acknowledgement of one webhook delivery")
public record PaymentWebhookReceiptDto(

        @Schema(description = "The provider's identifier of the event",
                example = "evt_5WHHcZPv7VS0YUsberIuOz")
        String eventId,

        @Schema(description = "Whether this delivery repeated one already processed, and so "
                + "changed nothing")
        boolean duplicate,

        @Schema(description = "What processing did; absent for a duplicate", example = "APPLIED")
        PaymentWebhookOutcome outcome) {

    /**
     * Acknowledges a delivery that processed its event.
     *
     * @param eventId the provider's identifier of the event
     * @param outcome what processing did
     * @return the receipt
     */
    public static PaymentWebhookReceiptDto processed(
            final String eventId,
            final PaymentWebhookOutcome outcome) {

        return new PaymentWebhookReceiptDto(eventId, false, outcome);
    }

    /**
     * Acknowledges a repeated delivery.
     *
     * @param eventId the provider's identifier of the event
     * @return the receipt
     */
    public static PaymentWebhookReceiptDto duplicate(final String eventId) {
        return new PaymentWebhookReceiptDto(eventId, true, null);
    }
}
