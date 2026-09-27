package com.kovospace.newtablinks.common.exceptions;

/**
 * Thrown when a call this service makes to the payment provider fails or answers with something
 * unusable.
 *
 * <p>Answered with 502: the caller did nothing wrong and may retry. The provider's own response
 * body is logged, never returned, since it can describe the merchant account.</p>
 *
 * @since 0.0.9
 */
public class PaymentProviderRequestFailedException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message explanation safe to return to the caller
     * @param cause   the underlying failure, or {@code null}
     */
    public PaymentProviderRequestFailedException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
