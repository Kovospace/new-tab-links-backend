package com.kovospace.newtablinks.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.auth.models.VisitorTokenOutcome;
import com.kovospace.newtablinks.auth.services.VisitorTokenService;
import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.config.ClientRequestHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the meter in front of the endpoints that disclose whether a username is registered.
 *
 * <p>Three of these cases are the ones worth having. <strong>Registration is metered too</strong>,
 * because it refuses a taken username with 409 and would otherwise be the same enumeration oracle
 * one endpoint to the left - the easiest possible mistake to make here is to throttle only the
 * lookup and believe the job is done. <strong>Too soon answers 429 and not 401</strong>, because
 * the website reacts to the two differently and a client that fetches a fresh token every time it
 * is going too fast turns a throttle into a retry storm. And <strong>a preflight passes
 * through</strong>, since a browser cannot send a custom header on {@code OPTIONS} and demanding
 * one there would break the site while every {@code curl} kept working.</p>
 *
 * @since 0.0.5
 */
class VisitorTokenAuthenticationFilterTest {

    /** A token value; what it says never matters, only what the service makes of it. */
    private static final String PRESENTED_TOKEN = "a-visitor-token";

    /** A path the filter must not touch. */
    private static final String UNGUARDED_PATH = "/api/v1/auth/login";

    private final VisitorTokenService visitorTokenService = mock(VisitorTokenService.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("a request whose token had a use left reaches the endpoint")
    void shouldLetThroughARequestWithAUsableToken() throws Exception {
        throttleIsOnAnd(VisitorTokenOutcome.ACCEPTED);
        final FilterChain chain = mock(FilterChain.class);

        final MockHttpServletResponse response = filter(
                requestTo("GET", ApiEndpointPaths.USERNAME_EXISTENCE_PATH, PRESENTED_TOKEN), chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("registration is metered too, because its 409 answers the same question")
    void shouldMeterRegistrationAsWellAsTheLookup() throws Exception {
        throttleIsOnAnd(VisitorTokenOutcome.MISSING);
        final FilterChain chain = mock(FilterChain.class);

        final MockHttpServletResponse response = filter(
                requestTo("POST", ApiEndpointPaths.REGISTRATION_PATH, null), chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("a missing, unknown, expired or spent token is answered with 401")
    void shouldAnswerAReplaceableTokenWithUnauthorized() throws Exception {
        for (final VisitorTokenOutcome outcome : new VisitorTokenOutcome[] {
                VisitorTokenOutcome.MISSING,
                VisitorTokenOutcome.UNKNOWN,
                VisitorTokenOutcome.EXPIRED,
                VisitorTokenOutcome.EXHAUSTED }) {

            throttleIsOnAnd(outcome);
            final FilterChain chain = mock(FilterChain.class);

            final MockHttpServletResponse response = filter(
                    requestTo("GET", ApiEndpointPaths.USERNAME_EXISTENCE_PATH, PRESENTED_TOKEN),
                    chain);

            assertThat(response.getStatus())
                    .as("%s should ask the caller for a new token", outcome)
                    .isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isNull();
            verify(chain, never()).doFilter(any(), any());
        }
    }

    @Test
    @DisplayName("a caller going too fast is answered with 429 and told how long to wait")
    void shouldAnswerARushedCallerWithTooManyRequests() throws Exception {
        throttleIsOnAnd(VisitorTokenOutcome.TOO_SOON);
        when(visitorTokenService.retryAfterSeconds()).thenReturn(1L);
        final FilterChain chain = mock(FilterChain.class);

        final MockHttpServletResponse response = filter(
                requestTo("GET", ApiEndpointPaths.USERNAME_EXISTENCE_PATH, PRESENTED_TOKEN), chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("a preflight passes through and spends nothing")
    void shouldLetAPreflightThrough() throws Exception {
        throttleIsOnAnd(VisitorTokenOutcome.MISSING);
        final FilterChain chain = mock(FilterChain.class);

        filter(requestTo("OPTIONS", ApiEndpointPaths.USERNAME_EXISTENCE_PATH, null), chain);

        verify(chain, times(1)).doFilter(any(), any());
        verify(visitorTokenService, never()).consumeOneUse(anyString());
    }

    @Test
    @DisplayName("an endpoint this filter does not guard is left alone")
    void shouldIgnoreAnUnguardedPath() throws Exception {
        throttleIsOnAnd(VisitorTokenOutcome.MISSING);
        final FilterChain chain = mock(FilterChain.class);

        filter(requestTo("POST", UNGUARDED_PATH, null), chain);

        verify(chain, times(1)).doFilter(any(), any());
        verify(visitorTokenService, never()).consumeOneUse(anyString());
    }

    @Test
    @DisplayName("switching the throttle off lets every guarded call through untouched")
    void shouldPassEverythingThroughWhenDisabled() throws Exception {
        when(visitorTokenService.isThrottleEnabled()).thenReturn(false);
        final FilterChain chain = mock(FilterChain.class);

        filter(requestTo("GET", ApiEndpointPaths.USERNAME_EXISTENCE_PATH, null), chain);

        verify(chain, times(1)).doFilter(any(), any());
        verify(visitorTokenService, never()).consumeOneUse(anyString());
    }

    /**
     * Switches the throttle on and fixes what the service will make of any presented token.
     *
     * @param outcome what the service should report
     */
    private void throttleIsOnAnd(final VisitorTokenOutcome outcome) {
        when(visitorTokenService.isThrottleEnabled()).thenReturn(true);
        when(visitorTokenService.consumeOneUse(any())).thenReturn(outcome);
    }

    /**
     * Runs one request through the filter.
     *
     * @param request     the request to run
     * @param filterChain stand-in for the rest of the chain
     * @return the response the caller would receive
     * @throws ServletException when the chain fails
     * @throws IOException      when the rejection body cannot be written
     */
    private MockHttpServletResponse filter(
            final MockHttpServletRequest request,
            final FilterChain filterChain) throws ServletException, IOException {

        final MockHttpServletResponse response = new MockHttpServletResponse();
        new VisitorTokenAuthenticationFilter(visitorTokenService, objectMapper)
                .doFilter(request, response, filterChain);
        return response;
    }

    /**
     * Builds a request, optionally carrying the visitor token header.
     *
     * @param method          HTTP method
     * @param path            path to request
     * @param presentedToken  value of the token header; {@code null} sends no header at all
     * @return the request
     */
    private static MockHttpServletRequest requestTo(
            final String method, final String path, final String presentedToken) {

        final MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (presentedToken != null) {
            request.addHeader(ClientRequestHeaders.VISITOR_TOKEN, presentedToken);
        }
        return request;
    }
}
