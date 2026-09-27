package com.kovospace.newtablinks.common.exceptions;

/**
 * Thrown when a payment operation is requested but this deployment has no provider credentials
 * for it.
 *
 * <p>Answered with 503 rather than 500: nothing is broken, the feature is switched off. A local
 * development instance runs this way by default, and so does any deployment that has not been
 * given the provider's secrets yet - the webhook then refuses every delivery instead of accepting
 * ones it cannot verify.</p>
 *
 * @since 0.0.9
 */
public class PaymentProviderNotConfiguredException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message explanation safe to return to the caller; names no secret
     */
    public PaymentProviderNotConfiguredException(final String message) {
        super(message);
    }
}
