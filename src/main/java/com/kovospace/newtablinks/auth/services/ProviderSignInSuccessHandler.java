package com.kovospace.newtablinks.auth.services;

import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.user.models.AuthenticationProviderType;
import com.kovospace.newtablinks.user.models.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Runs at the end of a provider sign-in: resolves the account and sends the browser back to the
 * website carrying a single use handoff code.
 *
 * <p>The browser is redirected with a <em>code</em> rather than with tokens because a redirect
 * address ends up in browser history, in the referrer of anything the landing page loads, and in
 * any proxy log along the way. A code that dies in seconds and works once survives that exposure;
 * a refresh token would not.</p>
 *
 * @since 0.0.2
 */
@Component
public class ProviderSignInSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProviderSignInSuccessHandler.class);

    /**
     * OpenID Connect claim holding the provider's immutable identifier for the user.
     */
    private static final String SUBJECT_CLAIM = "sub";
    private static final String EMAIL_CLAIM = "email";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";
    private static final String NAME_CLAIM = "name";

    private final ProviderSignInService providerSignInService;
    private final SingleUseCodeService singleUseCodeService;
    private final WebApplicationProperties webApplicationProperties;

    /**
     * Creates the handler.
     *
     * @param providerSignInService    finds or creates the local account
     * @param singleUseCodeService     mints the handoff code
     * @param webApplicationProperties knows where to send the browser
     */
    public ProviderSignInSuccessHandler(
            final ProviderSignInService providerSignInService,
            final SingleUseCodeService singleUseCodeService,
            final WebApplicationProperties webApplicationProperties) {

        this.providerSignInService = providerSignInService;
        this.singleUseCodeService = singleUseCodeService;
        this.webApplicationProperties = webApplicationProperties;
    }

    @Override
    public void onAuthenticationSuccess(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Authentication authentication) throws IOException {

        final OAuth2User providerUser = (OAuth2User) authentication.getPrincipal();

        final UserEntity account = providerSignInService.resolveAccountForProviderSignIn(
                resolveProviderFrom(authentication),
                providerUser.getAttribute(SUBJECT_CLAIM),
                providerUser.getAttribute(EMAIL_CLAIM),
                Boolean.TRUE.equals(providerUser.getAttribute(EMAIL_VERIFIED_CLAIM)),
                providerUser.getAttribute(NAME_CLAIM));

        final String handoffCode = singleUseCodeService.mintWebSessionHandoffCode(account);

        LOGGER.debug("Provider sign-in completed for account {}, redirecting to the website",
                account.getId());

        response.sendRedirect(webApplicationProperties.buildOauthCallbackLink(handoffCode));
    }

    /**
     * Works out which provider performed the sign-in.
     *
     * @param authentication the completed provider authentication
     * @return the matching provider
     * @throws IllegalStateException when a provider is configured that this service has no enum
     *                               constant for, which would be a configuration mistake
     */
    private static AuthenticationProviderType resolveProviderFrom(final Authentication authentication) {
        final String registrationId = authentication instanceof OAuth2AuthenticationToken token
                ? token.getAuthorizedClientRegistrationId()
                : "";
        try {
            return AuthenticationProviderType.valueOf(registrationId.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException unknownProvider) {
            throw new IllegalStateException(
                    "Client registration '%s' has no matching AuthenticationProviderType"
                            .formatted(registrationId), unknownProvider);
        }
    }
}
