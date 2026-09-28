package com.kovospace.newtablinks.common.config;

import com.kovospace.newtablinks.admin.services.AdminAccessTokenIssuer;
import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.auth.services.ProviderSignInSuccessHandler;
import com.kovospace.newtablinks.auth.services.VisitorTokenService;
import com.kovospace.newtablinks.common.security.FrontendApiKeyAuthenticationFilter;
import com.kovospace.newtablinks.common.security.VisitorTokenAuthenticationFilter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * The service's security posture: what is public, what needs a token, and how tokens are signed.
 *
 * <p>Two client shapes have to coexist here. The <strong>website</strong> runs the provider
 * sign-in flow, which is a redirect-based browser flow. The <strong>browser extension</strong>
 * only ever sends a bearer token. So the chain enables both {@code oauth2Login} and the resource
 * server, and keeps sessions stateless for everything else.</p>
 *
 * @since 0.0.2
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfiguration.class);

    /**
     * Endpoints reachable without a token, because they are how a caller obtains one.
     */
    private static final String[] PUBLIC_AUTHENTICATION_ENDPOINTS = {
            ApiEndpointPaths.REGISTRATION_PATH,
            // Public because a visitor with no token has to be able to obtain one; the throttle
            // it feeds must never stand in front of it. See VisitorTokenAuthenticationFilter.
            ApiEndpointPaths.VISITOR_TOKEN_PATH,
            // The operator's way in, and the only admin path reachable without an admin token.
            // Not unguarded: AdminSignInService refuses it outright when no credentials are
            // configured, and locks it after too many failures.
            ApiEndpointPaths.ADMINISTRATION_SIGN_IN_PATH,
            "/api/v1/auth/activate",
            "/api/v1/auth/resend-activation",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/session-handoff",
            "/api/v1/auth/extension-connect",
            "/api/v1/auth/password/reset-request",
            "/api/v1/auth/password/reset-confirm",
            // Public to the resource server, but not unguarded: the frontend API key filter and
            // the visitor token filter both stand in front of it. Registration above is metered
            // by the second of those too, because its 409 answers the same question.
            ApiEndpointPaths.USERNAME_EXISTENCE_PATH
    };

    /**
     * Endpoints that take anonymous usage counts, open to {@code POST} without a token.
     *
     * <p>Also the endpoints on which a bearer token is not even read - see
     * {@link #buildBearerTokenResolver()}.</p>
     */
    private static final String[] ANONYMOUS_STATISTICS_ENDPOINTS = {
            ApiEndpointPaths.NEW_TAB_STATISTICS_PATH,
            ApiEndpointPaths.WEBSITE_VISIT_STATISTICS_PATH
    };

    /**
     * Request headers a browser client is allowed to set on a call to this API.
     *
     * <p>Listed explicitly rather than as {@code "*"}: the origin list is explicit too, and with
     * {@code allowCredentials(true)} a wildcard does not mean "no headers matter" - Spring echoes
     * back whatever the caller asked for, so any header a compromised or careless script invents
     * would be accepted forever without anyone deciding to accept it. A handful of entries is a
     * contract small enough to state.</p>
     *
     * <p>Why exactly these, and nothing else:</p>
     * <ul>
     *   <li>{@code Authorization} - the bearer token on every authenticated call.</li>
     *   <li>{@code Content-Type} - request bodies are {@code application/json}, which is not one
     *       of the CORS-safelisted content types and therefore triggers a preflight.</li>
     *   <li>{@link ClientRequestHeaders#DEVICE_NAME} - read by the three endpoints that issue
     *       tokens, to label a row in the user's device list.</li>
     *   <li>{@link ClientRequestHeaders#INSTALLATION_ID} - read by the same three endpoints, and
     *       what actually identifies the device the names merely label. Sent by the extension
     *       rather than the website, but listed here for the same reason as the rest: a browser
     *       will not send a header the preflight has not allowed, and the extension's calls are
     *       ordinary cross-origin requests.</li>
     *   <li>{@link ClientRequestHeaders#FRONTEND_API_KEY} - the website's shared key, demanded by
     *       {@link FrontendApiKeyAuthenticationFilter} on the endpoints reserved for the site.
     *       Omitting it here would not merely weaken the guard, it would make the guarded
     *       endpoints unreachable: the browser would refuse to send the header at all.</li>
     *   <li>{@link ClientRequestHeaders#VISITOR_TOKEN} - the metered pass demanded by
     *       {@link VisitorTokenAuthenticationFilter} on registration and the username lookup.
     *       Same trap as the entry above: leave it out and the website cannot register anybody.
     *       </li>
     * </ul>
     *
     * <p>Two headers the endpoints also read are deliberately absent. {@code User-Agent} is a
     * forbidden header name: the browser sets it and a script cannot, so it never appears in a
     * preflight request. {@code Accept} is CORS-safelisted for the values Angular sends. Adding
     * either would be noise.</p>
     *
     * <p>This is a compile-time constant and not a configuration property on purpose. It states
     * what the API's own clients send, which is the same in every deployment, unlike the origins
     * they send it from. Making it configurable would only create a way for a deployment to break
     * sign-in by forgetting an entry.</p>
     */
    private static final List<String> ALLOWED_CORS_REQUEST_HEADERS = List.of(
            "Authorization",
            "Content-Type",
            ClientRequestHeaders.DEVICE_NAME,
            ClientRequestHeaders.INSTALLATION_ID,
            ClientRequestHeaders.FRONTEND_API_KEY,
            ClientRequestHeaders.VISITOR_TOKEN);

    /**
     * Endpoints that document the service or report its health.
     */
    private static final String[] PUBLIC_SUPPORT_ENDPOINTS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };

    private final AuthenticationProperties authenticationProperties;
    private final WebApplicationProperties webApplicationProperties;
    private final ObjectMapper objectMapper;
    private final VisitorTokenService visitorTokenService;
    private final String[] allowedCorsOrigins;

    /**
     * Creates the configuration.
     *
     * @param authenticationProperties signing secret and token lifetimes
     * @param webApplicationProperties supplies the website's shared API key
     * @param objectMapper             renders the error body a rejecting filter writes itself
     * @param visitorTokenService      meters the endpoints open to anonymous visitors
     * @param allowedCorsOrigins       origins permitted to call the API from a browser
     */
    public SecurityConfiguration(
            final AuthenticationProperties authenticationProperties,
            final WebApplicationProperties webApplicationProperties,
            final ObjectMapper objectMapper,
            final VisitorTokenService visitorTokenService,
            @Value("${newtablinks.security.allowed-cors-origins}") final String[] allowedCorsOrigins) {

        this.authenticationProperties = authenticationProperties;
        this.webApplicationProperties = webApplicationProperties;
        this.objectMapper = objectMapper;
        this.visitorTokenService = visitorTokenService;
        this.allowedCorsOrigins = allowedCorsOrigins.clone();
    }

    /**
     * Builds the filter chain.
     *
     * <p>Provider sign-in is wired only when at least one provider is actually configured. Spring
     * Boot validates client registrations at startup and refuses to run with an empty client id,
     * so declaring Google unconditionally would make credentials mandatory just to boot - and the
     * service has to start in development, in tests, and in any deployment that does not offer
     * Google at all.</p>
     *
     * @param httpSecurity                 chain under construction
     * @param providerSignInSuccessHandler what happens once a provider has vouched for a user
     * @param clientRegistrations          the configured providers, absent when there are none
     * @return the configured chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            final HttpSecurity httpSecurity,
            final ProviderSignInSuccessHandler providerSignInSuccessHandler,
            final ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {

        httpSecurity
                // No browser session carries privileges here, and every state-changing call is
                // authorised by a bearer token rather than a cookie, so there is no CSRF vector
                // to defend. The provider sign-in flow is protected by its own state parameter.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_SUPPORT_ENDPOINTS).permitAll()
                        .requestMatchers(PUBLIC_AUTHENTICATION_ENDPOINTS).permitAll()
                        // Everything else under /api/v1/admin demands the authority that only an
                        // operator sign-in grants. Declared before the catch-all below, because
                        // `authenticated()` would otherwise let any user's token through to
                        // endpoints that can read, change and delete every account.
                        .requestMatchers(ApiEndpointPaths.ADMINISTRATION_PATH_PATTERN)
                        .hasAuthority(AdminAccessTokenIssuer.ADMIN_AUTHORITY)
                        // The websocket handshake is open on purpose: a browser cannot set an
                        // Authorization header on a WebSocket, so the token travels in the STOMP
                        // CONNECT frame instead and is checked by StompAuthenticationInterceptor.
                        // A socket that never connects successfully can do nothing.
                        .requestMatchers("/ws/**").permitAll()
                        // The payment provider's webhook. It has no token of ours; the HMAC of
                        // its body is the authentication, checked by the provider adapter before
                        // anything is read. Without this line the catch-all below answers every
                        // delivery with 401, the provider gives up after five attempts, and the
                        // first sign is a customer who paid and got nothing.
                        .requestMatchers(HttpMethod.POST, ApiEndpointPaths.CREEM_WEBHOOK_PATH)
                        .permitAll()
                        // Anonymous usage counts from the extension and the website. Rate
                        // limited per address in the controller, and never tied to an account.
                        .requestMatchers(HttpMethod.POST, ANONYMOUS_STATISTICS_ENDPOINTS)
                        .permitAll()
                        .anyRequest().authenticated())
                // Bearer flow: every API call from the website and the extension.
                .oauth2ResourceServer(server -> server
                        .bearerTokenResolver(buildBearerTokenResolver())
                        .jwt(Customizer.withDefaults()))
                // Guards the handful of endpoints reserved for the website. Placed after the
                // CORS filter so its 403 still carries the CORS headers a browser needs before
                // it will let the site read the status - otherwise every rejection would reach
                // the website as an unexplained CORS failure instead.
                .addFilterAfter(buildFrontendApiKeyAuthenticationFilter(), CorsFilter.class)
                // Meters registration and the username lookup. Placed after the key filter
                // rather than after the CORS filter as well, so that the order of the two is
                // stated here instead of left to whichever was registered last: the cheap
                // string comparison rejects before the metered one reaches the database.
                .addFilterAfter(
                        buildVisitorTokenAuthenticationFilter(),
                        FrontendApiKeyAuthenticationFilter.class);

        // Browser flow: the website sends the user here to sign in with a provider.
        if (clientRegistrations.getIfAvailable() != null) {
            httpSecurity.oauth2Login(login -> login.successHandler(providerSignInSuccessHandler));
            LOGGER.info("Provider sign-in is enabled");
        } else {
            LOGGER.warn("No OAuth2 client is configured, so provider sign-in is disabled. "
                    + "Set SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID and "
                    + "..._CLIENT_SECRET to enable it.");
        }

        return httpSecurity.build();
    }

    /**
     * Builds the resolver that finds the bearer token on a request, except where none may count.
     *
     * <p>On {@link #ANONYMOUS_STATISTICS_ENDPOINTS} it finds nothing, even when an
     * {@code Authorization} header is there. The resource server rejects an expired or invalid
     * token with 401 even on a {@code permitAll} path, so a stale token would otherwise lose a
     * report that needs no token at all - and ignoring it outright is also the guarantee that a
     * count is never tied to an account.</p>
     *
     * @return the resolver
     */
    private static BearerTokenResolver buildBearerTokenResolver() {
        final BearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        final RequestMatcher anonymousStatisticsEndpoints = new OrRequestMatcher(
                Arrays.stream(ANONYMOUS_STATISTICS_ENDPOINTS)
                        .map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.POST, path))
                        .toList());
        return request -> anonymousStatisticsEndpoints.matches(request)
                ? null
                : defaultResolver.resolve(request);
    }

    /**
     * Builds the filter guarding the endpoints reserved for the website.
     *
     * <p>Constructed rather than declared as a bean on purpose: a {@code Filter} bean is picked
     * up by Spring Boot's servlet filter auto-registration as well, which would run it a second
     * time outside the security chain and outside the CORS filter it has to follow.</p>
     *
     * @return the filter, already carrying the configured key
     */
    private FrontendApiKeyAuthenticationFilter buildFrontendApiKeyAuthenticationFilter() {
        final FrontendApiKeyAuthenticationFilter filter = new FrontendApiKeyAuthenticationFilter(
                webApplicationProperties.frontendApiKey(), objectMapper);

        if (!filter.isFrontendApiKeyConfigured()) {
            LOGGER.warn("No frontend API key is configured, so the endpoints reserved for the "
                    + "website will refuse every call. Set FRONTEND_API_KEY to enable them.");
        }
        return filter;
    }

    /**
     * Builds the filter metering the endpoints open to anonymous visitors.
     *
     * <p>Constructed rather than declared as a bean for the same reason as the filter above: a
     * {@code Filter} bean would also be picked up by Spring Boot's servlet filter
     * auto-registration and run a second time, outside the security chain, where it would meter
     * every request twice.</p>
     *
     * @return the filter
     */
    private VisitorTokenAuthenticationFilter buildVisitorTokenAuthenticationFilter() {
        if (!visitorTokenService.isThrottleEnabled()) {
            LOGGER.warn("Visitor token throttling is switched off, so registration and the "
                    + "username lookup are open to unlimited automated calls. "
                    + "Set VISITOR_TOKEN_ENABLED=true to enable it.");
        }
        return new VisitorTokenAuthenticationFilter(visitorTokenService, objectMapper);
    }

    /**
     * Supplies the encoder used for user passwords.
     *
     * <p>{@link PasswordEncoderFactories#createDelegatingPasswordEncoder()} prefixes each hash
     * with the scheme that produced it, so the default can be strengthened later and old hashes
     * keep verifying without a migration.</p>
     *
     * @return the password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Builds the symmetric key both halves of the token lifecycle use.
     *
     * @return the HMAC signing key
     */
    private SecretKeySpec buildSigningKey() {
        return new SecretKeySpec(
                authenticationProperties.jwtSigningSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
    }

    /**
     * Supplies the encoder that signs access tokens.
     *
     * @return the JWT encoder
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(buildSigningKey()));
    }

    /**
     * Supplies the decoder that validates incoming access tokens.
     *
     * @return the JWT decoder
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(buildSigningKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Declares which browser origins may call this API, and with what.
     *
     * <p>The website and the extension live on different origins from the API, so neither can
     * call it without being listed here. The extension's origin is
     * {@code chrome-extension://<extension id>}. Origins are deployment-dependent and therefore
     * configured, through {@code newtablinks.security.allowed-cors-origins}.</p>
     *
     * <p>Everything else is stated in code, because it describes the API rather than the
     * environment: the methods it serves, and {@link #ALLOWED_CORS_REQUEST_HEADERS} - read its
     * Javadoc before changing the list, it explains why each entry is there.</p>
     *
     * <p>No response header is exposed. Clients read only the response body; nothing here answers
     * with a header a script has to see, so the CORS-safelisted response headers suffice and
     * {@code setExposedHeaders} would grant reach that no caller uses.</p>
     *
     * <p>The trap this configuration sets: {@code curl} does not enforce CORS, so a preflight that
     * silently drops a header still returns 200 and the request that follows still succeeds on the
     * command line. Only a real browser refuses. Verify a change against a browser, or against the
     * {@code Access-Control-Allow-Headers} response header, never against whether the call worked.
     * </p>
     *
     * @return the CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedCorsOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(ALLOWED_CORS_REQUEST_HEADERS);
        configuration.setAllowCredentials(true);

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
