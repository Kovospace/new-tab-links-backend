package com.kovospace.newtablinks.common.security;

import com.kovospace.newtablinks.auth.utils.AuthorizationRequestCookieCipher;
import com.kovospace.newtablinks.auth.utils.AuthorizationRequestCookieCodec;
import com.kovospace.newtablinks.auth.utils.AuthorizationRequestCookieCodec.DecodedAuthorizationRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

/**
 * Keeps a provider sign-in's authorization request in a sealed cookie instead of the HTTP
 * session, so that whichever replica Google's callback reaches can finish the sign-in.
 *
 * <p>Spring's default keeps it in the {@code HttpSession} of the pod that sent the browser to
 * Google. The rest of this API is stateless, and with more than one replica the callback lands
 * on another pod about as often as not, where the request is missing and sign-in fails with
 * {@code authorization_request_not_found}. A cookie travels with the browser instead.</p>
 *
 * <p>The cookie is:</p>
 * <ul>
 *   <li>sealed with AES-GCM ({@link AuthorizationRequestCookieCipher}), so it can be neither
 *       read nor forged by the browser holding it;</li>
 *   <li>{@code HttpOnly} and {@code Secure}, and scoped to {@value #COOKIE_PATH} - sent back
 *       only to the callback, never to the API;</li>
 *   <li>{@code SameSite=Lax}: the callback is a top-level cross-site navigation from Google,
 *       which a {@code Lax} cookie accompanies and a {@code Strict} one would not;</li>
 *   <li>honoured for the provider sign-in lifetime only, judged by the time sealed inside it,
 *       so a browser that kept it past its {@code Max-Age} gains nothing.</li>
 * </ul>
 *
 * <p>Loading follows Spring's own repository: the callback's {@code state} parameter must be
 * present and match the saved request, or nothing is found. A missing, stale, altered or foreign
 * cookie also finds nothing, which Spring reports as {@code authorization_request_not_found} -
 * the correct answer to a callback this service did not start.</p>
 *
 * @since 0.0.12
 */
public final class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    /** Name of the cookie carrying the sealed request. */
    public static final String COOKIE_NAME = "newtablinks_oauth2_authorization_request";

    /** Covers {@code /login/oauth2/code/{registrationId}}, the only place the cookie is read. */
    public static final String COOKIE_PATH = "/login/oauth2";

    /** Sent to the provider callback, a top-level navigation from another site. */
    private static final String COOKIE_SAME_SITE = "Lax";

    private final AuthorizationRequestCookieCipher cookieCipher;
    private final AuthorizationRequestCookieCodec cookieCodec;
    private final Duration providerSignInLifetime;
    private final Clock clock;

    /**
     * Creates the repository.
     *
     * @param cookieCipher           seals and opens the cookie; keyed identically on every replica
     * @param cookieCodec            turns the request into JSON and back
     * @param providerSignInLifetime how long after leaving for the provider the callback is accepted
     * @param clock                  the time the request is stamped and judged by
     */
    public CookieOAuth2AuthorizationRequestRepository(
            final AuthorizationRequestCookieCipher cookieCipher,
            final AuthorizationRequestCookieCodec cookieCodec,
            final Duration providerSignInLifetime,
            final Clock clock) {

        this.cookieCipher = cookieCipher;
        this.cookieCodec = cookieCodec;
        this.providerSignInLifetime = providerSignInLifetime;
        this.clock = clock;
    }

    /**
     * Finds the request the callback answers.
     *
     * @param request the provider's callback
     * @return the saved request, or {@code null} when there is none, it is stale or unreadable,
     *         or its state does not match the callback's
     */
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(final HttpServletRequest request) {
        final String callbackState = request.getParameter(OAuth2ParameterNames.STATE);
        if (callbackState == null) {
            return null;
        }
        return readStoredAuthorizationRequest(request)
                .filter(stored -> callbackState.equals(stored.getState()))
                .orElse(null);
    }

    /**
     * Stores the request in the cookie before the browser is sent to the provider, or clears the
     * cookie when there is nothing to store.
     *
     * @param authorizationRequest the request, or {@code null} to remove it
     * @param request              the request that starts the sign-in
     * @param response             the redirect to the provider, which carries the cookie
     */
    @Override
    public void saveAuthorizationRequest(
            final OAuth2AuthorizationRequest authorizationRequest,
            final HttpServletRequest request,
            final HttpServletResponse response) {

        if (authorizationRequest == null) {
            expireCookie(response);
            return;
        }
        final String sealedRequest =
                cookieCipher.seal(cookieCodec.encode(authorizationRequest, clock.instant()));
        writeCookie(response, sealedRequest, providerSignInLifetime);
    }

    /**
     * Takes the request out of the cookie as the callback is handled, and clears the cookie
     * whether or not one was found, so a spent request is never offered again.
     *
     * @param request  the provider's callback
     * @param response the response to it
     * @return the saved request, as {@link #loadAuthorizationRequest} would find it
     */
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            final HttpServletRequest request,
            final HttpServletResponse response) {

        final OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        expireCookie(response);
        return authorizationRequest;
    }

    /**
     * Opens, parses and dates the cookie on a request.
     *
     * @param request any request
     * @return the saved request when the cookie is present, authentic and still fresh
     */
    private Optional<OAuth2AuthorizationRequest> readStoredAuthorizationRequest(
            final HttpServletRequest request) {

        return findCookieValue(request)
                .flatMap(cookieCipher::open)
                .flatMap(cookieCodec::decode)
                .filter(this::isWithinProviderSignInLifetime)
                .map(DecodedAuthorizationRequest::authorizationRequest);
    }

    /**
     * Whether a request was saved recently enough to be finished.
     *
     * @param decoded the request with the moment it was sealed
     * @return {@code true} while the provider sign-in lifetime has not run out
     */
    private boolean isWithinProviderSignInLifetime(final DecodedAuthorizationRequest decoded) {
        return !clock.instant().isAfter(decoded.issuedAt().plus(providerSignInLifetime));
    }

    /**
     * Finds this repository's cookie on a request.
     *
     * @param request any request
     * @return its value, or empty when the browser sent none
     */
    private static Optional<String> findCookieValue(final HttpServletRequest request) {
        final Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    /**
     * Tells the browser to forget the cookie.
     *
     * @param response the response to add the instruction to
     */
    private static void expireCookie(final HttpServletResponse response) {
        writeCookie(response, "", Duration.ZERO);
    }

    /**
     * Adds the cookie to a response with every attribute it needs.
     *
     * <p>Written through {@link ResponseCookie} because the servlet {@link Cookie} has no way to
     * say {@code SameSite}.</p>
     *
     * @param response the response to add it to
     * @param value    the sealed request, or empty when expiring
     * @param maximumAge how long the browser keeps it; zero deletes it
     */
    private static void writeCookie(
            final HttpServletResponse response,
            final String value,
            final Duration maximumAge) {

        final ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite(COOKIE_SAME_SITE)
                .path(COOKIE_PATH)
                .maxAge(maximumAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
