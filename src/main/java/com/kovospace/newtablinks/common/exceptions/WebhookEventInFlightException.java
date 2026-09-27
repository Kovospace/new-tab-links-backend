package com.kovospace.newtablinks.common.exceptions;

/**
 * Thrown when a webhook event is redelivered while an earlier delivery of it is still being
 * processed.
 *
 * <p>Answered with 409, which the provider treats as a failed delivery and retries later - by
 * which time the first delivery has either finished, and the retry is acknowledged as a duplicate,
 * or has been abandoned long enough for the retry to take it over.</p>
 *
 * @since 0.0.9
 */
public class WebhookEventInFlightException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param providerEventId the provider's identifier of the event
     */
    public WebhookEventInFlightException(final String providerEventId) {
        super("Webhook event " + providerEventId + " is already being processed");
    }
}
