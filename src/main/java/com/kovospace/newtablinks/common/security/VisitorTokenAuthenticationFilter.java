package com.kovospace.newtablinks.common.security;

import com.kovospace.newtablinks.auth.models.VisitorTokenOutcome;
import com.kovospace.newtablinks.auth.services.VisitorTokenService;
import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.config.ClientRequestHeaders;
import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Meters the two endpoints that disclose whether a username is registered.
 *
 * <p>Registration refuses a taken username with 409, and the lookup that serves the registration
 * form answers the same question directly. Both are account enumeration oracles and neither can
 * be closed without losing a feature people actually want, so what this filter takes away is the
 * speed: a caller must present a token issued by
 * {@link ApiEndpointPaths#VISITOR_TOKEN_PATH}, and that token may be spent only as fast, and
 * only as often, as
 * {@link com.kovospace.newtablinks.auth.config.VisitorTokenProperties} allows.</p>
 *
 * <p><strong>What it is not.</strong> Tokens are free and unlimited, because the visitor is
 * anonymous and there is nothing about them to check. Somebody willing to hold many tokens at
 * once gets their throughput back. This is a speed limit, not a gate, and its Javadoc says so
 * where nobody can miss it: never put anything behind this filter that would be damaged by a
 * patient caller.</p>
 *
 * <p>Two statuses, meaning two different things to a client:</p>
 * <ul>
 *   <li><strong>401</strong> - the token is missing, unknown, expired or spent. Ask for a new one
 *       and carry on.</li>
 *   <li><strong>429</strong> - the token is fine but the call came too soon, with
 *       {@code Retry-After} saying how long to wait. A new token would not help; a fresh one has
 *       its own first-use delay.</li>
 * </ul>
 *
 * <p>Placed after {@link FrontendApiKeyAuthenticationFilter}, which is itself after
 * {@link org.springframework.web.filter.CorsFilter}, for the reason spelled out there: a
 * rejection a browser cannot read is indistinguishable from a broken server.</p>
 *
 * <p>Preflight requests pass straight through. A browser sends {@code OPTIONS} without custom
 * headers - finding out whether it may send one is the point - so demanding the token there would
 * make the real request impossible.</p>
 *
 * @since 0.0.5
 */
public class VisitorTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(VisitorTokenAuthenticationFilter.class);

    /**
     * Paths this filter meters.
     */
    private static final Set<String> GUARDED_PATHS = Set.of(
            ApiEndpointPaths.USERNAME_EXISTENCE_PATH,
            ApiEndpointPaths.REGISTRATION_PATH);

    /**
     * Wording for a token that cannot be used again and has to be replaced.
     */
    private static final String REPLACE_TOKEN_MESSAGE =
            "This endpoint requires a valid " + ClientRequestHeaders.VISITOR_TOKEN
                    + " header. Obtain one from " + ApiEndpointPaths.VISITOR_TOKEN_PATH
                    + " and try again.";

    /**
     * Wording for a caller going faster than the token allows.
     */
    private static final String SLOW_DOWN_MESSAGE =
            "That was too soon. Wait for the interval stated when the token was issued.";

    private final VisitorTokenService visitorTokenService;
    private final ObjectMapper objectMapper;

    /**
     * Creates the filter.
     *
     * @param visitorTokenService decides whether a presented token may spend a call
     * @param objectMapper        renders the rejection body in the API's standard shape
     */
    public VisitorTokenAuthenticationFilter(
            final VisitorTokenService visitorTokenService,
            final ObjectMapper objectMapper) {

        this.visitorTokenService = visitorTokenService;
        this.objectMapper = objectMapper;
    }

    /**
     * Limits the filter to the guarded paths, and never to a preflight.
     *
     * @param request the incoming request
     * @return {@code true} when this request must not be metered
     */
    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !visitorTokenService.isThrottleEnabled()
                || !GUARDED_PATHS.contains(pathWithinApplication(request));
    }

    /**
     * Spends one use of the presented token, or refuses the call.
     *
     * @param request     the incoming request
     * @param response    the response being built
     * @param filterChain the rest of the chain
     * @throws ServletException when the rest of the chain fails
     * @throws IOException      when the rejection body cannot be written
     */
    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {

        final VisitorTokenOutcome outcome = visitorTokenService.consumeOneUse(
                request.getHeader(ClientRequestHeaders.VISITOR_TOKEN));

        if (outcome.isAccepted()) {
            filterChain.doFilter(request, response);
            return;
        }

        LOGGER.debug("Refusing {}: visitor token {}", pathWithinApplication(request), outcome);

        if (outcome == VisitorTokenOutcome.TOO_SOON) {
            response.setHeader(HttpHeaders.RETRY_AFTER,
                    Long.toString(visitorTokenService.retryAfterSeconds()));
            writeRejection(response, HttpStatus.TOO_MANY_REQUESTS, SLOW_DOWN_MESSAGE);
            return;
        }

        writeRejection(response, HttpStatus.UNAUTHORIZED, REPLACE_TOKEN_MESSAGE);
    }

    /**
     * Writes a refusal in the same body shape every other failure in this API uses.
     *
     * <p>Written here rather than thrown, because a filter runs before the dispatcher and
     * therefore out of reach of
     * {@link com.kovospace.newtablinks.common.exceptions.GlobalExceptionHandler}.</p>
     *
     * @param response the response being built
     * @param status   the status to answer with
     * @param message  the explanation to carry
     * @throws IOException when the body cannot be written
     */
    private void writeRejection(
            final HttpServletResponse response,
            final HttpStatus status,
            final String message) throws IOException {

        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponseDto(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                List.of()));
    }

    /**
     * Returns the request path with any deployment context path removed.
     *
     * @param request the incoming request
     * @return the path to match against {@link #GUARDED_PATHS}
     */
    private static String pathWithinApplication(final HttpServletRequest request) {
        final String requestUri = request.getRequestURI();
        final String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isEmpty() || !requestUri.startsWith(contextPath)) {
            return requestUri;
        }
        return requestUri.substring(contextPath.length());
    }
}
