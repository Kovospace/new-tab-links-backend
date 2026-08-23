package com.kovospace.newtablinks.common.exceptions;

/**
 * Thrown when a credential is rejected.
 *
 * <p>The message is deliberately uniform across every cause - unknown username, wrong password,
 * unactivated account, locked account. Telling a caller <em>which</em> of those happened tells an
 * attacker which usernames exist and which passwords are close, so all of them surface as the
 * same 401. The specific reason is written to the log instead.</p>
 *
 * @since 0.0.2
 */
public class AuthenticationFailedException extends RuntimeException {

    /**
     * Wording returned to every caller regardless of the real cause.
     */
    private static final String UNIFORM_MESSAGE = "Invalid credentials";

    /**
     * Creates the exception with the uniform message.
     */
    public AuthenticationFailedException() {
        super(UNIFORM_MESSAGE);
    }
}
