package com.kovospace.newtablinks.common.exceptions;

/**
 * Thrown when a webhook delivery is not signed with this service's webhook secret.
 *
 * <p>Missing, malformed and wrong signatures are answered identically, with 401, so that a caller
 * probing the endpoint learns nothing about which part it got wrong.</p>
 *
 * @since 0.0.9
 */
public class WebhookSignatureRejectedException extends RuntimeException {

    /**
     * Creates the exception with the one message every rejected signature gets.
     */
    public WebhookSignatureRejectedException() {
        super("Webhook signature is missing or invalid");
    }
}
