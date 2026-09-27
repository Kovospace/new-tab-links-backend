package com.kovospace.newtablinks.common.exceptions;

/**
 * Thrown when the operator tries to take pro away from an account that paid for it.
 *
 * <p>The operator can take back only what the operator gave. A lifetime purchase or a
 * subscription still in its paid period is ended by a refund or a cancellation at the payment
 * provider, whose webhook then updates the entitlement; clearing it here would leave the provider
 * charging, or holding money, for something the account no longer has.</p>
 *
 * @since 0.0.10
 */
public class PaidEntitlementRevocationException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message explanation safe to return to the caller
     */
    public PaidEntitlementRevocationException(final String message) {
        super(message);
    }
}
