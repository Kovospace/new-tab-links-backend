package com.kovospace.newtablinks.admin.services;

import com.kovospace.newtablinks.admin.config.AdminAccessProperties;
import com.kovospace.newtablinks.admin.dtos.AdminSessionDto;
import com.kovospace.newtablinks.admin.dtos.AdminSignInRequestDto;
import com.kovospace.newtablinks.common.exceptions.AuthenticationFailedException;
import com.kovospace.newtablinks.common.exceptions.TooManyAttemptsException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Checks the operator's credentials and hands back a short-lived admin token.
 *
 * <p>The only way into the admin surface. Everything it protects is irreversible, so the order of
 * business here matters: the lock is consulted <em>before</em> the credentials, a failure is
 * counted whatever went wrong, and every refusal is worded identically.</p>
 *
 * @since 0.0.6
 */
@Service
public class AdminSignInService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminSignInService.class);

    private final AdminAccessProperties adminAccessProperties;
    private final AdminSignInAttemptTracker signInAttemptTracker;
    private final AdminAccessTokenIssuer adminAccessTokenIssuer;

    /**
     * Creates the service.
     *
     * @param adminAccessProperties  the configured operator identity
     * @param signInAttemptTracker   counts failures and imposes the lock
     * @param adminAccessTokenIssuer mints the token a successful sign-in returns
     */
    public AdminSignInService(
            final AdminAccessProperties adminAccessProperties,
            final AdminSignInAttemptTracker signInAttemptTracker,
            final AdminAccessTokenIssuer adminAccessTokenIssuer) {

        this.adminAccessProperties = adminAccessProperties;
        this.signInAttemptTracker = signInAttemptTracker;
        this.adminAccessTokenIssuer = adminAccessTokenIssuer;
    }

    /**
     * Signs the operator in.
     *
     * @param signInRequest the credentials presented
     * @return the admin session
     * @throws TooManyAttemptsException      when sign-in is currently locked
     * @throws AuthenticationFailedException when the credentials are wrong, or none are configured
     */
    public AdminSessionDto signIn(final AdminSignInRequestDto signInRequest) {
        final Duration remainingLock = signInAttemptTracker.remainingLock();
        if (!remainingLock.isZero()) {
            // Checked first, and without looking at what was sent: a locked-out caller must not
            // be able to keep testing guesses and read the answer from which refusal comes back.
            throw new TooManyAttemptsException(
                    "Too many failed attempts. Try again later.", remainingLock);
        }

        if (!adminAccessProperties.isAdministrationEnabled()) {
            LOGGER.warn("An administrator sign-in was attempted, but no admin credentials are "
                    + "configured. Set ADMIN_USERNAME and ADMIN_PASSWORD to enable it.");
            signInAttemptTracker.recordFailure();
            throw new AuthenticationFailedException();
        }

        if (!presentsTheConfiguredCredentials(signInRequest)) {
            signInAttemptTracker.recordFailure();
            throw new AuthenticationFailedException();
        }

        signInAttemptTracker.recordSuccess();
        LOGGER.info("Administrator signed in");

        return new AdminSessionDto(
                adminAccessTokenIssuer.issueAdminAccessToken(),
                adminAccessTokenIssuer.expiryOfATokenIssuedNow());
    }

    /**
     * Compares the presented credentials with the configured ones.
     *
     * <p>Both halves are compared in time-constant fashion, and <em>both</em> are always compared
     * even when the first already failed. A short-circuit would leak, through the time it takes to
     * answer, whether the username was right - which is the more valuable half to a guesser,
     * because it is the half that never changes.</p>
     *
     * @param signInRequest the credentials presented
     * @return {@code true} when both match exactly
     */
    private boolean presentsTheConfiguredCredentials(final AdminSignInRequestDto signInRequest) {
        final boolean usernameMatches =
                constantTimeEquals(signInRequest.username(), adminAccessProperties.username());
        final boolean passwordMatches =
                constantTimeEquals(signInRequest.password(), adminAccessProperties.password());

        return usernameMatches & passwordMatches;
    }

    /**
     * Compares two values without letting the time taken reveal how far they matched.
     *
     * @param presented  what the caller sent
     * @param configured what the deployment holds
     * @return {@code true} when they are byte-for-byte equal
     */
    private static boolean constantTimeEquals(final String presented, final String configured) {
        if (presented == null || configured == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                configured.getBytes(StandardCharsets.UTF_8));
    }
}
