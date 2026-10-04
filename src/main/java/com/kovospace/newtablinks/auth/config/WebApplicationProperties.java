package com.kovospace.newtablinks.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the public website lives, bound from {@code newtablinks.web.*}.
 *
 * <p>The backend has to build absolute links into the website in two places - the activation
 * email, and the redirect that ends a provider sign-in - so the site's address cannot be
 * hardcoded. The website is a separate project and its address differs per environment.</p>
 *
 * <p>It also holds the shared key the website presents on the handful of endpoints reserved for
 * it, because that key identifies the same client this record already describes. Whether the key
 * is usable at all is decided by
 * {@link com.kovospace.newtablinks.common.security.FrontendApiKeyAuthenticationFilter}, which is
 * the only thing that reads it: blank or absent means every guarded call is refused.</p>
 *
 * @param baseUrl              root address of the website, without a trailing slash
 * @param activationPath       path on the website that receives an activation token
 * @param oauthCallbackPath    path on the website that receives the sign-in handoff code
 * @param passwordResetPath    path on the website that receives a password reset token
 * @param frontendApiKey       value the website sends in
 *                             {@link com.kovospace.newtablinks.common.config.ClientRequestHeaders#FRONTEND_API_KEY};
 *                             blank or absent disables every endpoint that requires it
 * @param devicesPath          path of the website's devices page, where a user sees what each
 *                             installation synchronises and signs installations out; sent as
 *                             {@code manageUrl} with every plan-limit refusal
 * @since 0.0.2
 */
@ConfigurationProperties(prefix = "newtablinks.web")
public record WebApplicationProperties(
        String baseUrl,
        String activationPath,
        String oauthCallbackPath,
        String passwordResetPath,
        String frontendApiKey,
        String devicesPath) {

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
     * Builds the absolute address of the website's devices page.
     *
     * @return the absolute link, without parameters
     * @since 0.0.18
     */
    public String buildDevicesPageLink() {
        return baseUrl + devicesPath;
    }

    /**
     * Builds the absolute password reset link mailed to a user.
     *
     * @param passwordResetToken the raw token to embed
     * @return the absolute link
     */
    public String buildPasswordResetLink(final String passwordResetToken) {
        return "%s%s?token=%s".formatted(
                baseUrl,
                passwordResetPath,
                java.net.URLEncoder.encode(passwordResetToken, java.nio.charset.StandardCharsets.UTF_8));
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
