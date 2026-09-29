package com.kovospace.newtablinks.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every tunable of the authentication subsystem, bound from {@code newtablinks.auth.*}.
 *
 * <p>Gathered into one type rather than scattered {@code @Value} fields so the whole security
 * posture of the service can be read in one place, and so a deployment can see what it is allowed
 * to override.</p>
 *
 * @param accessTokenLifetime         how long an issued access token stays valid
 * @param refreshTokenLifetime        how long a refresh token stays valid
 * @param activationTokenLifetime     how long an emailed activation link stays valid
 * @param passwordResetTokenLifetime  how long an emailed password reset link stays valid
 * @param webSessionHandoffLifetime   how long the code handed to the website after a provider
 *                                    sign-in stays valid
 * @param extensionConnectLifetime    how long a connect code shown to the user stays valid
 * @param providerSignInLifetime      how long a Google sign-in may take between leaving for the
 *                                    provider and coming back, which is how long the cookie
 *                                    carrying its authorization request is honoured
 * @param maximumFailedLoginAttempts  consecutive failures after which an account is locked
 * @param jwtSigningSecret            secret the access tokens are signed with
 * @param jwtIssuer                   value placed in the {@code iss} claim
 * @since 0.0.2
 */
@ConfigurationProperties(prefix = "newtablinks.auth")
public record AuthenticationProperties(
        Duration accessTokenLifetime,
        Duration refreshTokenLifetime,
        Duration activationTokenLifetime,
        Duration passwordResetTokenLifetime,
        Duration webSessionHandoffLifetime,
        Duration extensionConnectLifetime,
        Duration providerSignInLifetime,
        int maximumFailedLoginAttempts,
        String jwtSigningSecret,
        String jwtIssuer) {

    /**
     * Shortest signing secret accepted, in bytes. HMAC with SHA-256 requires a key at least as
     * long as its output, and a shorter one is rejected outright rather than silently weakening
     * every token this service issues.
     */
    private static final int MINIMUM_SIGNING_SECRET_BYTES = 32;

    /**
     * Validates the bound values.
     *
     * @throws IllegalStateException when the signing secret is too short to be safe
     */
    public AuthenticationProperties {
        if (jwtSigningSecret == null
                || jwtSigningSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                < MINIMUM_SIGNING_SECRET_BYTES) {

            throw new IllegalStateException(
                    ("newtablinks.auth.jwt-signing-secret must be at least %d bytes; set "
                            + "NEWTABLINKS_AUTH_JWT_SIGNING_SECRET to a long random value")
                            .formatted(MINIMUM_SIGNING_SECRET_BYTES));
        }
    }
}
