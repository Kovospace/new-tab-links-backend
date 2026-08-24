package com.kovospace.newtablinks.auth.services;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.dtos.ClientDescriptionDto;
import com.kovospace.newtablinks.auth.dtos.LoginRequestDto;
import com.kovospace.newtablinks.auth.dtos.TokenPairDto;
import com.kovospace.newtablinks.auth.models.RefreshTokenEntity;
import com.kovospace.newtablinks.auth.repositories.RefreshTokenRepository;
import com.kovospace.newtablinks.auth.utils.TokenHasher;
import com.kovospace.newtablinks.common.exceptions.AuthenticationFailedException;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserDeviceEntity;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signing in with a password, and keeping a session alive afterwards.
 *
 * @since 0.0.2
 */
@Service
public class AuthenticationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);

    /**
     * A real bcrypt hash of a value nobody knows, verified against when the named account does
     * not exist.
     *
     * <p>Its only purpose is to burn the same time a genuine verification would. Passing
     * {@code null} to the encoder instead is not an option: the delegating encoder throws on an
     * unrecognised hash format rather than returning {@code false}, which would turn a bad
     * username into a 500 and announce which usernames are real.</p>
     */
    private static final String DUMMY_PASSWORD_HASH =
            "{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenPairFactory tokenPairFactory;
    private final AuthenticationProperties authenticationProperties;

    /**
     * Creates the service.
     *
     * @param userRepository           reads accounts
     * @param refreshTokenRepository   reads and rotates refresh tokens
     * @param passwordEncoder          verifies submitted passwords
     * @param tokenPairFactory         issues the resulting tokens
     * @param authenticationProperties lockout threshold
     */
    public AuthenticationService(
            final UserRepository userRepository,
            final RefreshTokenRepository refreshTokenRepository,
            final PasswordEncoder passwordEncoder,
            final TokenPairFactory tokenPairFactory,
            final AuthenticationProperties authenticationProperties) {

        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenPairFactory = tokenPairFactory;
        this.authenticationProperties = authenticationProperties;
    }

    /**
     * Signs a client in with a username or address and a password.
     *
     * @param loginRequest      the submitted credentials
     * @param clientDescription where the request is coming from
     * @return a fresh token pair
     * @throws AuthenticationFailedException whenever sign-in does not succeed, for any reason
     */
    @Transactional
    public TokenPairDto login(
            final LoginRequestDto loginRequest,
            final ClientDescriptionDto clientDescription) {
        final Optional<UserEntity> possibleAccount =
                userRepository.findByUsernameOrEmail(loginRequest.usernameOrEmail().trim());

        if (possibleAccount.isEmpty()) {
            // Verify against a throwaway hash anyway, so that a missing account takes the same
            // time to reject as a wrong password. Skipping this leaks which usernames exist
            // through response timing alone.
            passwordEncoder.matches(loginRequest.password(), DUMMY_PASSWORD_HASH);
            LOGGER.debug("Sign-in attempt for an unknown username or address");
            throw new AuthenticationFailedException();
        }

        final UserEntity account = possibleAccount.get();

        if (!account.hasPassword()) {
            LOGGER.debug("Sign-in attempt on account {} which has no password", account.getId());
            throw new AuthenticationFailedException();
        }
        if (account.getStatus() != UserAccountStatus.ACTIVE) {
            LOGGER.debug("Sign-in attempt on account {} in status {}",
                    account.getId(), account.getStatus());
            throw new AuthenticationFailedException();
        }
        if (account.getFailedLoginAttempts() >= authenticationProperties.maximumFailedLoginAttempts()) {
            LOGGER.warn("Account {} is locked after {} consecutive failures",
                    account.getId(), account.getFailedLoginAttempts());
            throw new AuthenticationFailedException();
        }
        if (!passwordEncoder.matches(loginRequest.password(), account.getPasswordHash())) {
            account.recordFailedLoginAttempt();
            LOGGER.debug("Wrong password for account {} (attempt {})",
                    account.getId(), account.getFailedLoginAttempts());
            throw new AuthenticationFailedException();
        }

        account.resetFailedLoginAttempts();
        LOGGER.info("Account {} signed in", account.getId());
        return tokenPairFactory.issueTokenPairFor(account, clientDescription);
    }

    /**
     * Trades a refresh token for a new pair, retiring the token that was presented.
     *
     * <p>Rotating on every use is what keeps a stolen refresh token from being useful
     * indefinitely: the legitimate client's next refresh invalidates the thief's copy, or the
     * thief's use invalidates the client's, and either way somebody notices.</p>
     *
     * @param rawRefreshToken the token presented by the client
     * @return a fresh token pair
     * @throws AuthenticationFailedException when the token is unknown, spent, or its account can
     *                                       no longer sign in
     */
    @Transactional
    public TokenPairDto refresh(final String rawRefreshToken) {
        final RefreshTokenEntity storedToken = refreshTokenRepository
                .findByTokenHash(TokenHasher.hash(rawRefreshToken))
                .orElseThrow(AuthenticationFailedException::new);

        final Instant now = Instant.now();
        if (!storedToken.isUsableAt(now)) {
            LOGGER.warn("Refresh attempted with a revoked or expired token for account {}",
                    storedToken.getUser().getId());
            throw new AuthenticationFailedException();
        }

        final UserEntity account = storedToken.getUser();
        if (!account.isActive()) {
            throw new AuthenticationFailedException();
        }

        storedToken.revoke(now);

        // The device comes from the token, not from this request's headers: a client that
        // changed its reported device name mid-session would otherwise spawn a second device.
        final UserDeviceEntity device = storedToken.getDevice();
        device.markUsedAt(now);

        return tokenPairFactory.issueTokenPairForDevice(account, device);
    }

    /**
     * Ends one client's session by revoking the refresh token it presents.
     *
     * <p>Silently does nothing when the token is already unknown or spent - signing out should
     * never fail, and there is nothing useful for the caller to do about it.</p>
     *
     * @param rawRefreshToken the token to revoke
     */
    @Transactional
    public void logout(final String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(TokenHasher.hash(rawRefreshToken))
                .filter(token -> token.isUsableAt(Instant.now()))
                .ifPresent(token -> token.revoke(Instant.now()));
    }
}
