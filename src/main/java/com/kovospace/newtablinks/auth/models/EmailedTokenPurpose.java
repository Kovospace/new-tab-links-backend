package com.kovospace.newtablinks.auth.models;

/**
 * What an {@link EmailedTokenEntity} entitles its holder to do.
 *
 * <p>Part of the lookup, so a token mailed for one purpose can never be redeemed at the endpoint
 * serving the other.</p>
 *
 * @since 0.0.3
 */
public enum EmailedTokenPurpose {

    /**
     * Proves the address of a newly registered account and turns it
     * {@link com.kovospace.newtablinks.user.models.UserAccountStatus#ACTIVE}.
     */
    ACCOUNT_ACTIVATION,

    /**
     * Allows a new password to be set without knowing the old one.
     */
    PASSWORD_RESET
}
