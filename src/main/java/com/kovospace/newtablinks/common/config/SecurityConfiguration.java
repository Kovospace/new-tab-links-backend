package com.kovospace.newtablinks.common.config;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.services.ProviderSignInSuccessHandler;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
            "/api/v1/auth/register",
            "/api/v1/auth/activate",
            "/api/v1/auth/resend-activation",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/session-handoff",
            "/api/v1/auth/extension-connect",
            "/api/v1/auth/password/reset-request",
            "/api/v1/auth/password/reset-confirm"
    };

    /**
     * Request headers a browser client is allowed to set on a call to this API.
     *
     * <p>Listed explicitly rather than as {@code "*"}: the origin list is explicit too, and with
     * {@code allowCredentials(true)} a wildcard does not mean "no headers matter" - Spring echoes
     * back whatever the caller asked for, so any header a compromised or careless script invents
     * would be accepted forever without anyone deciding to accept it. Three entries is a contract
     * small enough to state.</p>
     *
     * <p>Why exactly these three, and nothing else:</p>
     * <ul>
     *   <li>{@code Authorization} - the bearer token on every authenticated call.</li>
     *   <li>{@code Content-Type} - request bodies are {@code application/json}, which is not one
     *       of the CORS-safelisted content types and therefore triggers a preflight.</li>
     *   <li>{@link ClientRequestHeaders#DEVICE_NAME} - read by the three endpoints that issue
     *       tokens, to label a row in the user's device list.</li>
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
            ClientRequestHeaders.DEVICE_NAME);

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
    private final String[] allowedCorsOrigins;

    /**
     * Creates the configuration.
     *
     * @param authenticationProperties signing secret and token lifetimes
     * @param allowedCorsOrigins       origins permitted to call the API from a browser
     */
    public SecurityConfiguration(
            final AuthenticationProperties authenticationProperties,
            @Value("${newtablinks.security.allowed-cors-origins}") final String[] allowedCorsOrigins) {

        this.authenticationProperties = authenticationProperties;
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
                        // The websocket handshake is open on purpose: a browser cannot set an
                        // Authorization header on a WebSocket, so the token travels in the STOMP
                        // CONNECT frame instead and is checked by StompAuthenticationInterceptor.
                        // A socket that never connects successfully can do nothing.
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().authenticated())
                // Bearer flow: every API call from the website and the extension.
                .oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults()));

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
