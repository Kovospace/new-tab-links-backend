package com.kovospace.newtablinks.admin.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kovospace.newtablinks.admin.config.AdminAccessProperties;
import com.kovospace.newtablinks.admin.dtos.AdminSignInRequestDto;
import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.common.exceptions.AuthenticationFailedException;
import com.kovospace.newtablinks.common.exceptions.TooManyAttemptsException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Verifies the one door into the admin surface.
 *
 * <p>Everything behind it is irreversible and none of it is ownership-scoped, so these are the
 * cases worth pinning: an unconfigured deployment refuses rather than admits; the lock is
 * consulted before the credentials, so a locked-out caller cannot keep testing guesses; and the
 * token that comes out carries the scope the endpoints demand and a subject that is not a user
 * identifier, which is what stops an operator from acting as somebody.</p>
 *
 * @since 0.0.6
 */
class AdminSignInServiceTest {

    /** A signing secret long enough for HMAC with SHA-256. */
    private static final String SIGNING_SECRET = "a-signing-secret-long-enough-for-hmac-sha256";

    private static final String CONFIGURED_USERNAME = "the-operator";
    private static final String CONFIGURED_PASSWORD = "a-very-long-operator-password";

    /** Failures allowed before the lock, matching the shipped default. */
    private static final int MAXIMUM_ATTEMPTS = 5;

    private JwtDecoder jwtDecoder;

    @BeforeEach
    void buildDecoder() {
        jwtDecoder = NimbusJwtDecoder.withSecretKey(signingKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Test
    @DisplayName("the right credentials return a token carrying the admin scope")
    void shouldIssueAnAdminScopedToken() {
        final var session = serviceWith(CONFIGURED_USERNAME, CONFIGURED_PASSWORD)
                .signIn(new AdminSignInRequestDto(CONFIGURED_USERNAME, CONFIGURED_PASSWORD));

        final var token = jwtDecoder.decode(session.accessToken());
        assertThat(token.getClaimAsString("scope")).isEqualTo(AdminAccessTokenIssuer.ADMIN_SCOPE);
        assertThat(session.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    @DisplayName("the token's subject is the operator, never a user identifier")
    void shouldNotIssueATokenThatCouldPassForAUser() {
        final var session = serviceWith(CONFIGURED_USERNAME, CONFIGURED_PASSWORD)
                .signIn(new AdminSignInRequestDto(CONFIGURED_USERNAME, CONFIGURED_PASSWORD));

        // AuthenticatedUserProvider parses this as a UUID; that it cannot be one is what stops an
        // operator's token from being usable on an ordinary endpoint.
        final String subject = jwtDecoder.decode(session.accessToken()).getSubject();
        assertThat(subject).isEqualTo(CONFIGURED_USERNAME);
        assertThatThrownBy(() -> java.util.UUID.fromString(subject))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a wrong password is refused")
    void shouldRefuseAWrongPassword() {
        assertThatThrownBy(() -> serviceWith(CONFIGURED_USERNAME, CONFIGURED_PASSWORD)
                .signIn(new AdminSignInRequestDto(CONFIGURED_USERNAME, "not-the-password")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("a deployment with no operator configured refuses rather than admits")
    void shouldFailClosedWhenNoOperatorIsConfigured() {
        assertThatThrownBy(() -> serviceWith("", "")
                .signIn(new AdminSignInRequestDto("anything", "anything")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("sign-in locks after the configured number of failures")
    void shouldLockAfterTooManyFailures() {
        final AdminSignInService signInService = serviceWith(CONFIGURED_USERNAME, CONFIGURED_PASSWORD);
        final var wrongCredentials = new AdminSignInRequestDto(CONFIGURED_USERNAME, "wrong");

        for (int attempt = 0; attempt < MAXIMUM_ATTEMPTS; attempt++) {
            assertThatThrownBy(() -> signInService.signIn(wrongCredentials))
                    .isInstanceOf(AuthenticationFailedException.class);
        }

        assertThatThrownBy(() -> signInService.signIn(wrongCredentials))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    @DisplayName("once locked, even the right credentials are refused without being looked at")
    void shouldRefuseTheRightCredentialsWhileLocked() {
        final AdminSignInService signInService = serviceWith(CONFIGURED_USERNAME, CONFIGURED_PASSWORD);
        final var wrongCredentials = new AdminSignInRequestDto(CONFIGURED_USERNAME, "wrong");

        for (int attempt = 0; attempt < MAXIMUM_ATTEMPTS; attempt++) {
            assertThatThrownBy(() -> signInService.signIn(wrongCredentials))
                    .isInstanceOf(AuthenticationFailedException.class);
        }

        // The whole point of checking the lock first: a locked-out caller must not be able to
        // tell a right guess from a wrong one by which refusal comes back.
        assertThatThrownBy(() -> signInService.signIn(
                new AdminSignInRequestDto(CONFIGURED_USERNAME, CONFIGURED_PASSWORD)))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    @DisplayName("a success in time clears the failures behind it")
    void shouldForgetFailuresAfterASuccess() {
        final AdminSignInService signInService = serviceWith(CONFIGURED_USERNAME, CONFIGURED_PASSWORD);
        final var wrongCredentials = new AdminSignInRequestDto(CONFIGURED_USERNAME, "wrong");

        for (int attempt = 0; attempt < MAXIMUM_ATTEMPTS - 1; attempt++) {
            assertThatThrownBy(() -> signInService.signIn(wrongCredentials))
                    .isInstanceOf(AuthenticationFailedException.class);
        }
        signInService.signIn(new AdminSignInRequestDto(CONFIGURED_USERNAME, CONFIGURED_PASSWORD));

        // The counter is back to zero, so this failure is the first one again rather than the
        // fifth, and does not lock.
        assertThatThrownBy(() -> signInService.signIn(wrongCredentials))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    /**
     * Builds the service under test around a given configured identity.
     *
     * @param username the configured operator name
     * @param password the configured operator password
     * @return the service
     */
    private AdminSignInService serviceWith(final String username, final String password) {
        final AdminAccessProperties adminProperties = new AdminAccessProperties(
                username, password, Duration.ofMinutes(30), MAXIMUM_ATTEMPTS, Duration.ofMinutes(5));

        return new AdminSignInService(
                adminProperties,
                new AdminSignInAttemptTracker(adminProperties),
                new AdminAccessTokenIssuer(jwtEncoder(), adminProperties, authenticationProperties()));
    }

    /**
     * @return an encoder using the test signing key
     */
    private static JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey()));
    }

    /**
     * @return the symmetric key both halves of the test use
     */
    private static SecretKeySpec signingKey() {
        return new SecretKeySpec(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * @return authentication settings valid enough to construct the issuer
     */
    private static AuthenticationProperties authenticationProperties() {
        return new AuthenticationProperties(
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                Duration.ofHours(24),
                Duration.ofHours(1),
                Duration.ofMinutes(2),
                Duration.ofMinutes(10),
                5,
                SIGNING_SECRET,
                "newtablinks-test");
    }
}
