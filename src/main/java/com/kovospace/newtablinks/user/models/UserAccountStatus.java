package com.kovospace.newtablinks.user.models;

/**
 * Lifecycle state of a user account.
 *
 * <p>Only {@link #ACTIVE} accounts may authenticate. Everything else is a closed door, which is
 * what makes the activation step meaningful: an account created by classic registration cannot
 * sync anything until its address has been proven.</p>
 *
 * @since 0.0.2
 */
public enum UserAccountStatus {

    /**
     * Created by classic registration; the address has not been proven yet.
     */
    PENDING_ACTIVATION,

    /**
     * Fully usable account.
     */
    ACTIVE,

    /**
     * Deliberately blocked. Kept distinct from deletion so the rows survive for audit.
     */
    DISABLED
}
