package com.kovospace.newtablinks.common.exceptions;

import java.time.Duration;

/**
 * Thrown when an anonymous caller has sent more requests than its address is allowed in one
 * window.
 *
 * <p>A {@link TooManyAttemptsException} because the answer is the same - 429 with a
 * {@code Retry-After} - but a distinct type because the cause is not: nothing was guessed and
 * nothing is locked, a busy address has simply used up its share. That difference is why it is
 * logged at debug rather than as a warning.</p>
 *
 * @since 0.0.11
 */
public class RequestRateLimitExceededException extends TooManyAttemptsException {

    /**
     * Creates the exception.
     *
     * @param retryAfter how long until the caller's window resets
     */
    public RequestRateLimitExceededException(final Duration retryAfter) {
        super("Too many requests from this address; try again later", retryAfter);
    }
}
