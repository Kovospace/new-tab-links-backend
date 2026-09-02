package com.kovospace.newtablinks.auth.services;

import com.kovospace.newtablinks.auth.config.VisitorTokenProperties;
import com.kovospace.newtablinks.auth.dtos.VisitorTokenDto;
import com.kovospace.newtablinks.auth.models.VisitorTokenEntity;
import com.kovospace.newtablinks.auth.models.VisitorTokenOutcome;
import com.kovospace.newtablinks.auth.repositories.VisitorTokenRepository;
import com.kovospace.newtablinks.auth.utils.SecureTokenGenerator;
import com.kovospace.newtablinks.auth.utils.TokenHasher;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues the metered passes anonymous visitors use, and decides whether a presented one may spend
 * a call.
 *
 * <p>This is the whole of the throttle. It holds no opinion about which endpoints are guarded -
 * that belongs to
 * {@link com.kovospace.newtablinks.common.security.VisitorTokenAuthenticationFilter} - and no
 * opinion about HTTP, which is why a refusal comes back as a
 * {@link VisitorTokenOutcome} rather than a status code.</p>
 *
 * @since 0.0.5
 */
@Service
public class VisitorTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VisitorTokenService.class);

    private final VisitorTokenRepository visitorTokenRepository;
    private final VisitorTokenProperties visitorTokenProperties;

    /**
     * Creates the service.
     *
     * @param visitorTokenRepository persistence for the issued tokens
     * @param visitorTokenProperties the configured lifetime, pace and quota
     */
    public VisitorTokenService(
            final VisitorTokenRepository visitorTokenRepository,
            final VisitorTokenProperties visitorTokenProperties) {

        this.visitorTokenRepository = visitorTokenRepository;
        this.visitorTokenProperties = visitorTokenProperties;
    }

    /**
     * Tells whether the guarded endpoints demand a token at all.
     *
     * @return {@code true} when the throttle is switched on
     */
    public boolean isThrottleEnabled() {
        return visitorTokenProperties.enabled();
    }

    /**
     * Issues a token to whoever asks.
     *
     * <p>Unauthenticated and unlimited by design: the caller is an anonymous visitor who has just
     * loaded a page, and there is nothing about them to check. The limits live on the token, not
     * on who may have one - see {@link VisitorTokenProperties} for what that does and does not
     * achieve.</p>
     *
     * @return the token and the rules it will be judged by
     */
    @Transactional
    public VisitorTokenDto issueToken() {
        final String rawToken = SecureTokenGenerator.generateMachineToken();
        final Instant expiresAt = Instant.now().plus(visitorTokenProperties.lifetime());

        visitorTokenRepository.save(new VisitorTokenEntity(TokenHasher.hash(rawToken), expiresAt));

        return new VisitorTokenDto(
                rawToken,
                expiresAt,
                visitorTokenProperties.minimumFirstUseDelay().toMillis(),
                visitorTokenProperties.minimumRequestInterval().toMillis(),
                visitorTokenProperties.maximumUses());
    }

    /**
     * Spends one call of a presented token, or explains why it may not be spent.
     *
     * <p>The decision is made by one conditional update, so two calls arriving together cannot
     * both be granted the same remaining use. Only once that update has already refused is the
     * row read, and only to name the reason - which is why the reason is a best-effort
     * explanation rather than a guarantee: a token exhausted a microsecond ago by another request
     * will be reported as exhausted, which is exactly what happened, just not necessarily
     * because of this caller.</p>
     *
     * @param presentedToken the raw token from the request, or {@code null} when there was none
     * @return what happened
     */
    @Transactional
    public VisitorTokenOutcome consumeOneUse(final String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return VisitorTokenOutcome.MISSING;
        }

        final Instant now = Instant.now();
        final String tokenHash = TokenHasher.hash(presentedToken);

        final int rowsUpdated = visitorTokenRepository.consumeOneUse(
                tokenHash,
                now,
                visitorTokenProperties.maximumUses(),
                now.minus(visitorTokenProperties.minimumFirstUseDelay()),
                now.minus(visitorTokenProperties.minimumRequestInterval()));

        if (rowsUpdated > 0) {
            return VisitorTokenOutcome.ACCEPTED;
        }

        final VisitorTokenOutcome refusal = explainRefusal(tokenHash, now);
        LOGGER.debug("Visitor token refused: {}", refusal);
        return refusal;
    }

    /**
     * Deletes every token whose lifetime has run out.
     *
     * @return how many rows were removed
     */
    @Transactional
    public int deleteExpiredTokens() {
        return visitorTokenRepository.deleteExpiredTokens(Instant.now());
    }

    /**
     * Works out which rule refused a call that the conditional update already declined.
     *
     * <p>The order matters: a token can be both expired and exhausted, and the caller is better
     * served by the reason that a new token would cure.</p>
     *
     * @param tokenHash hash of the presented token
     * @param now       moment being judged
     * @return the refusal reason
     */
    private VisitorTokenOutcome explainRefusal(final String tokenHash, final Instant now) {
        final Optional<VisitorTokenEntity> storedToken =
                visitorTokenRepository.findByTokenHash(tokenHash);

        if (storedToken.isEmpty()) {
            return VisitorTokenOutcome.UNKNOWN;
        }

        final VisitorTokenEntity visitorToken = storedToken.get();
        if (!now.isBefore(visitorToken.getExpiresAt())) {
            return VisitorTokenOutcome.EXPIRED;
        }
        if (visitorToken.getUsageCount() >= visitorTokenProperties.maximumUses()) {
            return VisitorTokenOutcome.EXHAUSTED;
        }
        return VisitorTokenOutcome.TOO_SOON;
    }

    /**
     * Returns how long a refused caller should wait before trying again.
     *
     * <p>Reported to a caller that arrived too soon, so that an honest client backs off by the
     * right amount instead of guessing.</p>
     *
     * @return the configured minimum interval between two calls, in whole seconds, at least one
     */
    public long retryAfterSeconds() {
        return Math.max(1L, visitorTokenProperties.minimumRequestInterval().toSeconds());
    }
}
