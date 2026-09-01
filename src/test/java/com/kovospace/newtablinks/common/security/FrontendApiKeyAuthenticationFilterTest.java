package com.kovospace.newtablinks.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.config.ClientRequestHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the gate in front of the endpoints reserved for the website.
 *
 * <p>Two of these cases are the ones worth having. The first is that an unset key <em>denies</em>
 * rather than permits: the endpoint behind this filter deliberately discloses whether a username
 * is registered, so a deployment that forgets to configure the key must go dark rather than
 * quietly become an open account enumeration service, and no other test would notice the
 * difference. The second is that a preflight passes through untouched - a browser cannot send a
 * custom header on an {@code OPTIONS} request, so demanding the key there would make the real
 * request impossible while every {@code curl} kept working.</p>
 *
 * @since 0.0.4
 */
class FrontendApiKeyAuthenticationFilterTest {

    /** The key a correctly configured deployment holds. */
    private static final String CONFIGURED_KEY = "a-frontend-api-key";

    /** The path this filter guards. */
    private static final String GUARDED_PATH = ApiEndpointPaths.USERNAME_EXISTENCE_PATH;

    /** A path the filter must not touch. */
    private static final String UNGUARDED_PATH = "/api/v1/auth/login";

    /** Pulls the {@code message} field out of a rendered error body. */
    private static final Pattern MESSAGE_FIELD = Pattern.compile("\"message\":\"([^\"]*)\"");

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("a request carrying the configured key reaches the endpoint")
    void shouldLetThroughARequestPresentingTheConfiguredKey() throws Exception {
        final FilterChain chain = mock(FilterChain.class);
        final MockHttpServletResponse response =
                filterWith(CONFIGURED_KEY, requestTo(GUARDED_PATH, CONFIGURED_KEY), chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("a request with no key at all is refused with 403 and never reaches the endpoint")
    void shouldRejectARequestWithoutTheKey() throws Exception {
        final FilterChain chain = mock(FilterChain.class);
        final MockHttpServletResponse response =
                filterWith(CONFIGURED_KEY, requestTo(GUARDED_PATH, null), chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("a request with the wrong key is refused exactly like one with no key")
    void shouldRejectARequestPresentingTheWrongKey() throws Exception {
        final FilterChain chain = mock(FilterChain.class);
        final MockHttpServletResponse response =
                filterWith(CONFIGURED_KEY, requestTo(GUARDED_PATH, "not-the-key"), chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("with no key configured every call is refused, including one sending a key")
    void shouldRejectEveryCallWhenNoKeyIsConfigured() throws Exception {
        final FilterChain chain = mock(FilterChain.class);
        final MockHttpServletResponse response =
                filterWith(null, requestTo(GUARDED_PATH, CONFIGURED_KEY), chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("a blank key counts as no key, and an empty header does not satisfy it")
    void shouldRejectEveryCallWhenTheConfiguredKeyIsBlank() throws Exception {
        final FilterChain chain = mock(FilterChain.class);
        final MockHttpServletResponse response =
                filterWith("   ", requestTo(GUARDED_PATH, ""), chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("the rejection is the API's standard error body, not an empty response")
    void shouldAnswerARejectionWithTheUniformErrorBody() throws Exception {
        final MockHttpServletResponse response = filterWith(
                CONFIGURED_KEY, requestTo(GUARDED_PATH, null), mock(FilterChain.class));

        assertThat(response.getHeader("Content-Type")).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"status\":403")
                .contains(ClientRequestHeaders.FRONTEND_API_KEY);
    }

    @Test
    @DisplayName("the rejection never says whether a key is configured or merely wrong")
    void shouldGiveTheSameWordingWhateverTheReasonForRefusing() throws Exception {
        final String withNoKeyConfigured = filterWith(
                null, requestTo(GUARDED_PATH, CONFIGURED_KEY), mock(FilterChain.class))
                .getContentAsString();
        final String withTheWrongKey = filterWith(
                CONFIGURED_KEY, requestTo(GUARDED_PATH, "not-the-key"), mock(FilterChain.class))
                .getContentAsString();

        assertThat(messageIn(withNoKeyConfigured)).isEqualTo(messageIn(withTheWrongKey));
    }

    @Test
    @DisplayName("a preflight passes through, because a browser cannot put the key on one")
    void shouldNotDemandTheKeyOnAPreflight() throws Exception {
        final FilterChain chain = mock(FilterChain.class);
        final MockHttpServletRequest preflight =
                new MockHttpServletRequest("OPTIONS", GUARDED_PATH);

        final MockHttpServletResponse response = filterWith(CONFIGURED_KEY, preflight, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("every other endpoint is left alone, key or no key")
    void shouldIgnoreRequestsToPathsItDoesNotGuard() throws Exception {
        final FilterChain chain = mock(FilterChain.class);
        final MockHttpServletResponse response =
                filterWith(CONFIGURED_KEY, requestTo(UNGUARDED_PATH, null), chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("a deployment context path does not smuggle a request past the guard")
    void shouldStillGuardThePathBehindAContextPath() throws Exception {
        final FilterChain chain = mock(FilterChain.class);
        final MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/backend" + GUARDED_PATH);
        request.setContextPath("/backend");

        final MockHttpServletResponse response = filterWith(CONFIGURED_KEY, request, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(chain, never()).doFilter(any(), any());
    }

    /**
     * Runs one request through a filter configured with the given key.
     *
     * @param configuredKey the key the deployment holds; {@code null} or blank means none
     * @param request       the request to run
     * @param filterChain   stand-in for the rest of the chain
     * @return the response the caller would receive
     * @throws ServletException when the chain fails
     * @throws IOException      when the rejection body cannot be written
     */
    private MockHttpServletResponse filterWith(
            final String configuredKey,
            final MockHttpServletRequest request,
            final FilterChain filterChain) throws ServletException, IOException {

        final MockHttpServletResponse response = new MockHttpServletResponse();
        new FrontendApiKeyAuthenticationFilter(configuredKey, objectMapper)
                .doFilter(request, response, filterChain);
        return response;
    }

    /**
     * Builds a GET request, optionally carrying the frontend API key header.
     *
     * @param path        path to request
     * @param presentedKey value of the key header; {@code null} sends no header at all
     * @return the request
     */
    private static MockHttpServletRequest requestTo(final String path, final String presentedKey) {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (presentedKey != null) {
            request.addHeader(ClientRequestHeaders.FRONTEND_API_KEY, presentedKey);
        }
        return request;
    }

    /**
     * Reads the {@code message} field out of a rendered error body.
     *
     * @param errorBody the JSON body written by the filter
     * @return the message it carries
     */
    private static String messageIn(final String errorBody) {
        final Matcher matcher = MESSAGE_FIELD.matcher(errorBody);
        assertThat(matcher.find()).as("the error body carries a message").isTrue();
        return matcher.group(1);
    }
}
