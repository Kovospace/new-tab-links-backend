package com.kovospace.newtablinks.common.exceptions;

import java.time.Duration;

/**
 * Thrown when a caller has been refused often enough that this service has stopped listening.
 *
 * <p>Distinct from {@link AuthenticationFailedException} because it says something different: the
 * credentials were not even looked at. Answering the two alike would let a caller who is already
 * locked out keep learning whether their guesses are right.</p>
 *
 * @since 0.0.6
 */
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    /**
     * Creates the exception.
     *
     * @param message    wording for the caller
     * @param retryAfter how much longer the refusal lasts
     */
    public TooManyAttemptsException(final String message, final Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    /**
     * Returns how much longer the refusal lasts.
     *
     * @return the remaining wait
     */
    public Duration getRetryAfter() {
        return retryAfter;
    }
}
