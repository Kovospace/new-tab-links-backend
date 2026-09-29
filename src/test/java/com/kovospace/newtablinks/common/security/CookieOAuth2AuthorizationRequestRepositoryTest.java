package com.kovospace.newtablinks.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.kovospace.newtablinks.auth.utils.AuthorizationRequestCookieCipher;
import com.kovospace.newtablinks.auth.utils.AuthorizationRequestCookieCodec;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies that a provider sign-in started on one replica can be finished on another, and that
 * nothing but an authentic, fresh, matching cookie finishes it.
 *
 * <p>Every "replica" here is a separate repository instance, and every callback a fresh request
 * carrying only the cookies the browser was given - no HTTP session, which is exactly what the
 * other pod does not have.</p>
 *
 * @since 0.0.12
 */
class CookieOAuth2AuthorizationRequestRepositoryTest {

    private static final String SHARED_SECRET = "a-signing-secret-long-enough-for-hmac-sha256";
    private static final String OTHER_SECRET = "a-different-secret-another-deployment-uses";
    private static final Duration SIGN_IN_LIFETIME = Duration.ofMinutes(10);
    private static final Instant STARTED_AT = Instant.parse("2026-09-29T10:00:00Z");
    private static final String STATE = "state-value-from-spring";

    @Test
    @DisplayName("a callback reaching another replica, with no session, finds the request")
    void shouldFindTheRequestOnAnotherReplicaWithoutAnHttpSession() {
        final MockHttpServletResponse redirectToProvider = startSignIn(replica(SHARED_SECRET, STARTED_AT));
        final MockHttpServletRequest callback = callbackCarrying(redirectToProvider, STATE);

        final OAuth2AuthorizationRequest found =
                replica(SHARED_SECRET, STARTED_AT.plusSeconds(30)).loadAuthorizationRequest(callback);

        assertThat(found).usingRecursiveComparison().isEqualTo(anAuthorizationRequest());
        assertThat(callback.getSession(false)).isNull();
    }

    @Test
    @DisplayName("the cookie is HttpOnly, Secure, SameSite=Lax, scoped to the callback, and expires")
    void shouldWriteACookieOnlyTheCallbackReceives() {
        final String setCookie = startSignIn(replica(SHARED_SECRET, STARTED_AT))
                .getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie)
                .startsWith(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME + "=")
                .contains("HttpOnly", "Secure", "SameSite=Lax",
                        "Path=" + CookieOAuth2AuthorizationRequestRepository.COOKIE_PATH,
                        "Max-Age=" + SIGN_IN_LIFETIME.toSeconds());
    }

    @Test
    @DisplayName("the cookie does not reveal the PKCE verifier or the state it carries")
    void shouldNotCarryTheRequestInReadableForm() {
        final Cookie cookie = cookieFrom(startSignIn(replica(SHARED_SECRET, STARTED_AT)));

        assertThat(cookie.getValue()).doesNotContain(STATE, "verifier", "accounts.google.com");
    }

    @Test
    @DisplayName("a cookie with one character changed finds nothing")
    void shouldIgnoreATamperedCookie() {
        final Cookie cookie = cookieFrom(startSignIn(replica(SHARED_SECRET, STARTED_AT)));
        final char[] tampered = cookie.getValue().toCharArray();
        final int middle = tampered.length / 2;
        tampered[middle] = tampered[middle] == 'A' ? 'B' : 'A';

        assertThat(replica(SHARED_SECRET, STARTED_AT)
                .loadAuthorizationRequest(callbackWithCookie(new String(tampered), STATE)))
                .isNull();
    }

    @Test
    @DisplayName("a cookie sealed under another secret finds nothing")
    void shouldIgnoreACookieSealedUnderAnotherSecret() {
        final MockHttpServletResponse redirect = startSignIn(replica(OTHER_SECRET, STARTED_AT));

        assertThat(replica(SHARED_SECRET, STARTED_AT)
                .loadAuthorizationRequest(callbackCarrying(redirect, STATE)))
                .isNull();
    }

    @Test
    @DisplayName("a cookie older than the sign-in lifetime finds nothing, even if the browser kept it")
    void shouldIgnoreAStaleCookie() {
        final MockHttpServletResponse redirect = startSignIn(replica(SHARED_SECRET, STARTED_AT));
        final Instant justTooLate = STARTED_AT.plus(SIGN_IN_LIFETIME).plusSeconds(1);

        assertThat(replica(SHARED_SECRET, justTooLate)
                .loadAuthorizationRequest(callbackCarrying(redirect, STATE)))
                .isNull();
    }

    @Test
    @DisplayName("a callback whose state does not match, or has none, finds nothing")
    void shouldIgnoreACallbackWithTheWrongState() {
        final MockHttpServletResponse redirect = startSignIn(replica(SHARED_SECRET, STARTED_AT));
        final CookieOAuth2AuthorizationRequestRepository repository = replica(SHARED_SECRET, STARTED_AT);

        assertThat(repository.loadAuthorizationRequest(callbackCarrying(redirect, "another-state")))
                .isNull();
        assertThat(repository.loadAuthorizationRequest(callbackCarrying(redirect, null))).isNull();
    }

    @Test
    @DisplayName("removing hands the request over and tells the browser to delete the cookie")
    void shouldExpireTheCookieWhenTheRequestIsRemoved() {
        final MockHttpServletResponse redirect = startSignIn(replica(SHARED_SECRET, STARTED_AT));
        final MockHttpServletResponse callbackResponse = new MockHttpServletResponse();

        final OAuth2AuthorizationRequest removed = replica(SHARED_SECRET, STARTED_AT)
                .removeAuthorizationRequest(callbackCarrying(redirect, STATE), callbackResponse);

        assertThat(removed).isNotNull();
        assertThat(callbackResponse.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME + "=;")
                .contains("Max-Age=0",
                        "Path=" + CookieOAuth2AuthorizationRequestRepository.COOKIE_PATH);
    }

    /**
     * One replica of the service, as the security configuration builds it.
     *
     * @param secret the JWT signing secret it was deployed with
     * @param now    the moment its clock reads
     * @return the repository
     */
    private static CookieOAuth2AuthorizationRequestRepository replica(
            final String secret, final Instant now) {

        return new CookieOAuth2AuthorizationRequestRepository(
                new AuthorizationRequestCookieCipher(secret),
                new AuthorizationRequestCookieCodec(JsonMapper.builder().build()),
                SIGN_IN_LIFETIME,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    /**
     * Saves a request the way Spring does before redirecting the browser to Google.
     *
     * @param repository the replica handling the start of the sign-in
     * @return the redirect response, carrying the cookie
     */
    private static MockHttpServletResponse startSignIn(
            final CookieOAuth2AuthorizationRequestRepository repository) {

        final MockHttpServletResponse redirectToProvider = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(
                anAuthorizationRequest(), new MockHttpServletRequest(), redirectToProvider);
        return redirectToProvider;
    }

    /**
     * The provider's callback as a browser sends it: fresh, with only the cookie it was given.
     *
     * @param redirectToProvider the response that set the cookie
     * @param state              the callback's state parameter, or {@code null} for none
     * @return the callback request
     */
    private static MockHttpServletRequest callbackCarrying(
            final MockHttpServletResponse redirectToProvider, final String state) {

        return callbackWithCookie(cookieFrom(redirectToProvider).getValue(), state);
    }

    /**
     * A callback carrying a given cookie value.
     *
     * @param cookieValue the value of the sign-in cookie
     * @param state       the callback's state parameter, or {@code null} for none
     * @return the callback request
     */
    private static MockHttpServletRequest callbackWithCookie(final String cookieValue, final String state) {
        final MockHttpServletRequest callback =
                new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        callback.setCookies(new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, cookieValue));
        if (state != null) {
            callback.setParameter(OAuth2ParameterNames.STATE, state);
        }
        return callback;
    }

    /**
     * Reads the sign-in cookie back out of a response.
     *
     * @param response a response the repository wrote to
     * @return the cookie
     */
    private static Cookie cookieFrom(final MockHttpServletResponse response) {
        return response.getCookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME);
    }

    /**
     * A Google login request shaped like Spring's own: PKCE and OpenID nonce included.
     *
     * @return the request
     */
    private static OAuth2AuthorizationRequest anAuthorizationRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("google-client-id")
                .redirectUri("https://api.tabilinks.app/login/oauth2/code/google")
                .scopes(Set.of("openid", "email", "profile"))
                .state(STATE)
                .additionalParameters(Map.of(
                        "code_challenge", "challenge-value",
                        "code_challenge_method", "S256",
                        "nonce", "nonce-hash"))
                .attributes(Map.of(
                        "registration_id", "google",
                        "code_verifier", "verifier-value",
                        "nonce", "nonce-value"))
                .authorizationRequestUri(
                        "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=x")
                .build();
    }
}
