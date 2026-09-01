package com.kovospace.newtablinks.common.security;

import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.config.ClientRequestHeaders;
import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 * Refuses the endpoints reserved for the website unless the caller presents the shared key.
 *
 * <p>A very small number of endpoints exist only to serve the website's own forms and would be
 * unwelcome as general-purpose API. {@link ApiEndpointPaths#USERNAME_EXISTENCE_PATH} is the first
 * of them: it answers whether a username is registered, which is exactly the disclosure the
 * registration endpoint goes out of its way to avoid, and is worth offering to the site's
 * registration form but not to the open internet.</p>
 *
 * <p><strong>The key is not a secret.</strong> It ships inside a public JavaScript bundle, so it
 * buys attribution and friction, never confidentiality. It stops these endpoints from being an
 * effortless third-party lookup service; it does not stop a determined caller who read the
 * bundle. Nothing behind this filter may rely on the key being unknown.</p>
 *
 * <p><strong>It fails closed.</strong> With no key configured, every guarded call is refused
 * rather than let through - a deployment that forgets
 * {@code newtablinks.web.frontend-api-key} loses one form's live feedback, instead of silently
 * publishing an account enumeration endpoint.</p>
 *
 * <p>Placed <em>after</em> {@link org.springframework.web.filter.CorsFilter} in the chain on
 * purpose. The rejection it writes is a normal cross-origin response, and a browser only lets a
 * script read the status when the CORS headers are present; refusing earlier would surface every
 * 403 to the website as an unexplained CORS error instead.</p>
 *
 * <p>Preflight requests pass straight through. A browser sends {@code OPTIONS} without any custom
 * header - discovering whether it may send one is the entire point of the preflight - so
 * demanding the key there would make the real request impossible.</p>
 *
 * @since 0.0.4
 */
public class FrontendApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FrontendApiKeyAuthenticationFilter.class);

    /**
     * Paths this filter guards.
     */
    private static final Set<String> GUARDED_PATHS = Set.of(ApiEndpointPaths.USERNAME_EXISTENCE_PATH);

    /**
     * Wording returned whether the key was missing, blank, wrong, or never configured.
     *
     * <p>Uniform on purpose: distinguishing the cases would tell a caller whether they are close,
     * and would let anyone probe whether a deployment has the feature switched on at all.</p>
     */
    private static final String REJECTION_MESSAGE =
            "This endpoint requires a valid " + ClientRequestHeaders.FRONTEND_API_KEY + " header.";

    private final String configuredFrontendApiKey;
    private final boolean frontendApiKeyIsConfigured;
    private final ObjectMapper objectMapper;

    /**
     * Creates the filter.
     *
     * @param configuredFrontendApiKey the expected key; {@code null} or blank refuses every call
     * @param objectMapper             renders the rejection body in the API's standard shape
     */
    public FrontendApiKeyAuthenticationFilter(
            final String configuredFrontendApiKey,
            final ObjectMapper objectMapper) {

        this.configuredFrontendApiKey = configuredFrontendApiKey;
        this.frontendApiKeyIsConfigured =
                configuredFrontendApiKey != null && !configuredFrontendApiKey.isBlank();
        this.objectMapper = objectMapper;
    }

    /**
     * Tells whether this filter has a usable key and can therefore let anything through.
     *
     * <p>Exposed so the security configuration can say so once at startup. Deciding it here
     * rather than accepting it from the caller is what makes "blank means deny" a property of the
     * filter itself, impossible to configure away by mistake.</p>
     *
     * @return {@code true} when a non-blank key is configured
     */
    public boolean isFrontendApiKeyConfigured() {
        return frontendApiKeyIsConfigured;
    }

    /**
     * Limits the filter to the guarded paths, and never to a preflight.
     *
     * @param request the incoming request
     * @return {@code true} when this request must not be checked
     */
    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !GUARDED_PATHS.contains(pathWithinApplication(request));
    }

    /**
     * Lets a correctly keyed request through and refuses everything else with 403.
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

        if (!frontendApiKeyIsConfigured) {
            LOGGER.warn("Refusing {} because no frontend API key is configured. "
                            + "Set FRONTEND_API_KEY to enable it.",
                    pathWithinApplication(request));
            writeRejection(response);
            return;
        }

        if (!presentsTheConfiguredKey(request)) {
            LOGGER.debug("Refusing {}: the frontend API key was missing or wrong",
                    pathWithinApplication(request));
            writeRejection(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Compares the presented key with the configured one.
     *
     * <p>The comparison is time-constant. That is cheap insurance rather than a real defence -
     * the key is public anyway - but it costs one method call and removes a whole class of
     * question from any later review.</p>
     *
     * @param request the incoming request
     * @return {@code true} when the header is present and matches exactly
     */
    private boolean presentsTheConfiguredKey(final HttpServletRequest request) {
        final String presentedKey = request.getHeader(ClientRequestHeaders.FRONTEND_API_KEY);
        if (presentedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presentedKey.getBytes(StandardCharsets.UTF_8),
                configuredFrontendApiKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes the 403 in the same body shape every other failure in this API uses.
     *
     * <p>Written here rather than thrown, because a filter runs before the dispatcher and
     * therefore out of reach of
     * {@link com.kovospace.newtablinks.common.exceptions.GlobalExceptionHandler}.</p>
     *
     * @param response the response being built
     * @throws IOException when the body cannot be written
     */
    private void writeRejection(final HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponseDto(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                REJECTION_MESSAGE,
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
