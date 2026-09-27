package com.kovospace.newtablinks.common.exceptions;

/**
 * Thrown when a correctly signed webhook delivery cannot be read.
 *
 * <p>Only ever raised after the signature has been verified, so the body really came from the
 * provider - which means the provider changed its format. Answered with 400; the provider retries,
 * and the log says what was missing.</p>
 *
 * @since 0.0.9
 */
public class MalformedWebhookPayloadException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what could not be read; names fields, never values
     */
    public MalformedWebhookPayloadException(final String message) {
        super(message);
    }
}
