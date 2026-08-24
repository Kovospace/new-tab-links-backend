package com.kovospace.newtablinks.common.exceptions;

/**
 * Thrown when a registration cannot proceed because the chosen username is taken.
 *
 * <p>Note what this is <em>not</em> used for: a duplicate email address. Refusing that visibly
 * would let anyone test which addresses are registered, so a duplicate address is answered with
 * the same success response as a fresh registration, and an explanatory mail is sent to the
 * address instead. A username, unlike an address, is public by nature and has to be refused
 * openly or registration could not work at all.</p>
 *
 * @since 0.0.2
 */
public class RegistrationConflictException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message explanation safe to return to the caller
     */
    public RegistrationConflictException(final String message) {
        super(message);
    }
}
