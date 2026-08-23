package com.kovospace.newtablinks.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the public website lives, bound from {@code newtablinks.web.*}.
 *
 * <p>The backend has to build absolute links into the website in two places - the activation
 * email, and the redirect that ends a provider sign-in - so the site's address cannot be
 * hardcoded. The website is a separate project and its address differs per environment.</p>
 *
 * @param baseUrl              root address of the website, without a trailing slash
 * @param activationPath       path on the website that receives an activation token
 * @param oauthCallbackPath    path on the website that receives the sign-in handoff code
 * @since 0.0.2
 */
@ConfigurationProperties(prefix = "newtablinks.web")
public record WebApplicationProperties(
        String baseUrl,
        String activationPath,
        String oauthCallbackPath) {

    /**
     * Builds the absolute activation link mailed to a newly registered user.
     *
     * @param activationToken the raw token to embed
     * @return the absolute link
     */
    public String buildActivationLink(final String activationToken) {
        return "%s%s?token=%s".formatted(
                baseUrl,
                activationPath,
                java.net.URLEncoder.encode(activationToken, java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Builds the address a provider sign-in redirects to once the account is known.
     *
     * @param handoffCode the single use code the website will exchange for tokens
     * @return the absolute redirect address
     */
    public String buildOauthCallbackLink(final String handoffCode) {
        return "%s%s?code=%s".formatted(
                baseUrl,
                oauthCallbackPath,
                java.net.URLEncoder.encode(handoffCode, java.nio.charset.StandardCharsets.UTF_8));
    }
}
