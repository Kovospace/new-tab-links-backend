package com.kovospace.newtablinks.user.models;

/**
 * External identity provider a user can be recognised by.
 *
 * <p>The name matches the Spring Security client registration id in lower case, so a value here
 * maps directly onto a {@code spring.security.oauth2.client.registration.<id>} block.</p>
 *
 * @since 0.0.2
 */
public enum AuthenticationProviderType {

    /**
     * Google, through OpenID Connect.
     */
    GOOGLE;

    /**
     * Returns the Spring Security client registration id this provider is configured under.
     *
     * @return the registration id, lower case
     */
    public String getClientRegistrationId() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
