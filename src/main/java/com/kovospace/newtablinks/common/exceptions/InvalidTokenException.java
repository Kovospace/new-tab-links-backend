package com.kovospace.newtablinks.common.exceptions;

/**
 * Thrown when a token or code presented by a caller is unknown, already spent, or expired.
 *
 * <p>As with {@link AuthenticationFailedException}, the three cases are answered identically so
 * that probing cannot distinguish "never existed" from "already used".</p>
 *
 * @since 0.0.2
 */
public class InvalidTokenException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message explanation safe to return to the caller
     */
    public InvalidTokenException(final String message) {
        super(message);
    }
}
