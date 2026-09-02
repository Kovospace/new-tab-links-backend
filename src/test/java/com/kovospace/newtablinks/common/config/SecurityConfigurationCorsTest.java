package com.kovospace.newtablinks.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.config.VisitorTokenProperties;
import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.auth.services.VisitorTokenService;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the CORS preflight answer the browser clients actually depend on.
 *
 * <p>This is covered because the failure it guards against is invisible from the command line:
 * {@code curl} does not enforce CORS, so a preflight that quietly drops a header still answers
 * 200 and the request that follows still succeeds - while every real browser refuses. The website
 * sends {@link ClientRequestHeaders#DEVICE_NAME} on the calls that issue tokens, so a preflight
 * that omits it blocks every sign-in from the site and nothing short of a browser notices.</p>
 *
 * <p>The configuration is exercised through {@link DefaultCorsProcessor}, the same component the
 * filter chain uses, rather than by reading the {@link CorsConfiguration} back - the point is the
 * response header a browser reads, not the field that produced it.</p>
 *
 * @since 0.0.3
 */
class SecurityConfigurationCorsTest {

    /** Origin the website runs on in development; also the shipped default. */
    private static final String WEBSITE_ORIGIN = "http://localhost:5173";

    /** Origin of the packed Chrome extension, matched by the {@code chrome-extension://*} pattern. */
    private static final String EXTENSION_ORIGIN = "chrome-extension://abcdefghijklmnopabcdefghijklmnop";

    /** A path that issues tokens, and therefore reads the device-name header. */
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    /** The path reserved for the website, which reads the frontend API key header. */
    private static final String USERNAME_EXISTENCE_PATH = ApiEndpointPaths.USERNAME_EXISTENCE_PATH;

    /**
     * Header names exactly as a browser sends them in a preflight: lower-cased, comma-separated.
     */
    private static final String DEVICE_NAME_PREFLIGHT_HEADERS = "content-type,x-device-name";

    private final CorsConfigurationSource corsConfigurationSource =
            new SecurityConfiguration(
                    anAuthenticationConfiguration(),
                    aWebsiteConfiguration(),
                    JsonMapper.builder().build(),
                    aVisitorTokenService(),
                    allowedOrigins())
                    .corsConfigurationSource();

    @Test
    @DisplayName("the website's sign-in preflight is answered with the device-name header allowed")
    void shouldAllowTheDeviceNameHeaderForTheWebsiteOrigin() throws IOException {
        final MockHttpServletResponse response =
                sendPreflight(WEBSITE_ORIGIN, DEVICE_NAME_PREFLIGHT_HEADERS);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(allowedRequestHeadersIn(response))
                .contains(ClientRequestHeaders.DEVICE_NAME.toLowerCase(Locale.ROOT));
    }

    @Test
    @DisplayName("the extension gets the same answer, so it can name its device too")
    void shouldAllowTheDeviceNameHeaderForTheExtensionOrigin() throws IOException {
        final MockHttpServletResponse response =
                sendPreflight(EXTENSION_ORIGIN, DEVICE_NAME_PREFLIGHT_HEADERS);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(allowedRequestHeadersIn(response))
                .contains(ClientRequestHeaders.DEVICE_NAME.toLowerCase(Locale.ROOT));
    }

    @Test
    @DisplayName("the bearer token and a JSON body stay allowed")
    void shouldAllowTheAuthorizationAndContentTypeHeaders() throws IOException {
        final MockHttpServletResponse response =
                sendPreflight(WEBSITE_ORIGIN, "authorization,content-type");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(allowedRequestHeadersIn(response))
                .contains("authorization", "content-type");
    }

    @Test
    @DisplayName("the website's username lookup preflight allows the frontend API key header")
    void shouldAllowTheFrontendApiKeyHeaderForTheWebsiteOrigin() throws IOException {
        final MockHttpServletResponse response = sendPreflight(
                WEBSITE_ORIGIN,
                ClientRequestHeaders.FRONTEND_API_KEY.toLowerCase(Locale.ROOT),
                USERNAME_EXISTENCE_PATH,
                "GET");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(allowedRequestHeadersIn(response))
                .contains(ClientRequestHeaders.FRONTEND_API_KEY.toLowerCase(Locale.ROOT));
    }

    @Test
    @DisplayName("the website's registration preflight allows the visitor token header")
    void shouldAllowTheVisitorTokenHeaderForTheWebsiteOrigin() throws IOException {
        final MockHttpServletResponse response = sendPreflight(
                WEBSITE_ORIGIN,
                "content-type," + ClientRequestHeaders.VISITOR_TOKEN.toLowerCase(Locale.ROOT),
                ApiEndpointPaths.REGISTRATION_PATH,
                "POST");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(allowedRequestHeadersIn(response))
                .contains(ClientRequestHeaders.VISITOR_TOKEN.toLowerCase(Locale.ROOT));
    }

    @Test
    @DisplayName("GET is an allowed method, so the username lookup preflight is not refused")
    void shouldAllowGetForTheUsernameExistenceLookup() throws IOException {
        final MockHttpServletResponse response = sendPreflight(
                WEBSITE_ORIGIN,
                ClientRequestHeaders.FRONTEND_API_KEY.toLowerCase(Locale.ROOT),
                USERNAME_EXISTENCE_PATH,
                "GET");

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .contains("GET");
    }

    @Test
    @DisplayName("a header nobody declared is still refused, so the list means something")
    void shouldRejectAPreflightAskingForAnUndeclaredHeader() throws IOException {
        final MockHttpServletResponse response = sendPreflight(WEBSITE_ORIGIN, "x-made-up-header");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("an origin outside the configured list is refused whatever it asks for")
    void shouldRejectAPreflightFromAnUnknownOrigin() throws IOException {
        final MockHttpServletResponse response =
                sendPreflight("https://evil.example", DEVICE_NAME_PREFLIGHT_HEADERS);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    /**
     * Runs one preflight through the real CORS processor.
     *
     * @param origin          value of the {@code Origin} header
     * @param requestedHeaders value of {@code Access-Control-Request-Headers}
     * @return the response the browser would receive
     * @throws IOException when the processor cannot write the rejection body
     */
    private MockHttpServletResponse sendPreflight(final String origin, final String requestedHeaders)
            throws IOException {
        return sendPreflight(origin, requestedHeaders, LOGIN_PATH, "POST");
    }

    /**
     * Runs one preflight for a given path and method through the real CORS processor.
     *
     * @param origin           value of the {@code Origin} header
     * @param requestedHeaders value of {@code Access-Control-Request-Headers}
     * @param path             path the browser intends to call
     * @param intendedMethod   value of {@code Access-Control-Request-Method}
     * @return the response the browser would receive
     * @throws IOException when the processor cannot write the rejection body
     */
    private MockHttpServletResponse sendPreflight(
            final String origin,
            final String requestedHeaders,
            final String path,
            final String intendedMethod) throws IOException {

        final MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, intendedMethod);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, requestedHeaders);

        final MockHttpServletResponse response = new MockHttpServletResponse();
        new DefaultCorsProcessor().processRequest(
                corsConfigurationSource.getCorsConfiguration(request), request, response);
        return response;
    }

    /**
     * Reads {@code Access-Control-Allow-Headers} back as the browser would compare it.
     *
     * @param response the preflight response
     * @return the allowed header names, lower-cased; empty when the header is absent
     */
    private List<String> allowedRequestHeadersIn(final MockHttpServletResponse response) {
        final String allowed = response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        if (allowed == null) {
            return List.of();
        }
        return Arrays.stream(allowed.split(","))
                .map(header -> header.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * The origins the shipped default configures, as the security configuration receives them.
     *
     * @return the allowed origin patterns
     */
    private static String[] allowedOrigins() {
        return new String[] {WEBSITE_ORIGIN, "chrome-extension://*"};
    }

    /**
     * Authentication settings valid enough to construct the configuration under test.
     *
     * <p>Only the signing secret matters here - the record rejects a short one - and none of the
     * values influence CORS.</p>
     *
     * @return usable authentication properties
     */
    private static AuthenticationProperties anAuthenticationConfiguration() {
        return new AuthenticationProperties(
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                Duration.ofHours(24),
                Duration.ofHours(1),
                Duration.ofMinutes(2),
                Duration.ofMinutes(10),
                5,
                "a-signing-secret-long-enough-for-hmac-sha256",
                "newtablinks-test");
    }

    /**
     * Website settings valid enough to construct the configuration under test.
     *
     * <p>None of them influence CORS; the record is only there because the configuration reads
     * the frontend API key out of it when it builds its filter.</p>
     *
     * @return usable website properties
     */
    private static WebApplicationProperties aWebsiteConfiguration() {
        return new WebApplicationProperties(
                WEBSITE_ORIGIN,
                "/activate",
                "/auth/callback",
                "/reset-password",
                "a-frontend-api-key");
    }

    /**
     * A visitor token service valid enough to construct the configuration under test.
     *
     * <p>The repository is deliberately {@code null}: nothing in this test reaches persistence,
     * and the configuration only asks the service whether the throttle is switched on.</p>
     *
     * @return a service backed by nothing but its configuration
     */
    private static VisitorTokenService aVisitorTokenService() {
        return new VisitorTokenService(null, new VisitorTokenProperties(
                true,
                Duration.ofHours(1),
                Duration.ofMillis(500),
                Duration.ofMillis(200),
                250,
                Duration.ofHours(1)));
    }
}
