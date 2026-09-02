package com.kovospace.newtablinks.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.auth.config.VisitorTokenProperties;
import com.kovospace.newtablinks.auth.dtos.VisitorTokenDto;
import com.kovospace.newtablinks.auth.models.VisitorTokenEntity;
import com.kovospace.newtablinks.auth.models.VisitorTokenOutcome;
import com.kovospace.newtablinks.auth.repositories.VisitorTokenRepository;
import com.kovospace.newtablinks.auth.utils.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Verifies the throttle's decisions, and the two properties of it that matter most.
 *
 * <p>The first is that a granted use is decided by <em>one</em> conditional update and never by a
 * read followed by a write - the whole point of the design is that two simultaneous calls cannot
 * both be granted the last remaining use, and a read-then-write implementation would pass every
 * single-threaded test while failing exactly when it is being attacked.</p>
 *
 * <p>The second is that the raw token never reaches persistence. It is a low-stakes value, but
 * "we store the hash" is the kind of claim that quietly stops being true.</p>
 *
 * @since 0.0.5
 */
class VisitorTokenServiceTest {

    /** How long a token lives in these tests. */
    private static final Duration LIFETIME = Duration.ofHours(1);

    /** How long after issue the first call must wait. */
    private static final Duration FIRST_USE_DELAY = Duration.ofMillis(500);

    /** Shortest gap allowed between two calls. */
    private static final Duration REQUEST_INTERVAL = Duration.ofMillis(200);

    /** How many calls one token may make. */
    private static final int MAXIMUM_USES = 250;

    private final VisitorTokenRepository visitorTokenRepository = mock(VisitorTokenRepository.class);

    private final VisitorTokenService visitorTokenService =
            new VisitorTokenService(visitorTokenRepository, properties(true));

    @Test
    @DisplayName("an issued token is returned in full but stored only as a hash")
    void shouldStoreOnlyTheHashOfAnIssuedToken() {
        final VisitorTokenDto issued = visitorTokenService.issueToken();

        final ArgumentCaptor<VisitorTokenEntity> saved =
                ArgumentCaptor.forClass(VisitorTokenEntity.class);
        verify(visitorTokenRepository).save(saved.capture());

        assertThat(issued.token()).isNotBlank();
        assertThat(saved.getValue().getTokenHash())
                .isNotEqualTo(issued.token())
                .isEqualTo(TokenHasher.hash(issued.token()));
    }

    @Test
    @DisplayName("an issued token carries the limits it will be judged by")
    void shouldPublishTheLimitsAlongsideTheToken() {
        final VisitorTokenDto issued = visitorTokenService.issueToken();

        assertThat(issued.minimumFirstUseDelayMilliseconds()).isEqualTo(FIRST_USE_DELAY.toMillis());
        assertThat(issued.minimumRequestIntervalMilliseconds()).isEqualTo(REQUEST_INTERVAL.toMillis());
        assertThat(issued.maximumUses()).isEqualTo(MAXIMUM_USES);
        assertThat(issued.expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("a use the conditional update granted is accepted")
    void shouldAcceptAUseTheDatabaseGranted() {
        when(visitorTokenRepository.consumeOneUse(anyString(), any(), anyInt(), any(), any()))
                .thenReturn(1);

        assertThat(visitorTokenService.consumeOneUse("a-token"))
                .isEqualTo(VisitorTokenOutcome.ACCEPTED);
        verify(visitorTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("the decision is made from the hash, against the configured cut-off moments")
    void shouldJudgeTheHashAgainstTheConfiguredCutOffs() {
        when(visitorTokenRepository.consumeOneUse(anyString(), any(), anyInt(), any(), any()))
                .thenReturn(1);

        visitorTokenService.consumeOneUse("a-token");

        final ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        final ArgumentCaptor<Instant> latestIssue = ArgumentCaptor.forClass(Instant.class);
        final ArgumentCaptor<Instant> latestPreviousUse = ArgumentCaptor.forClass(Instant.class);
        verify(visitorTokenRepository).consumeOneUse(
                eq(TokenHasher.hash("a-token")),
                now.capture(),
                eq(MAXIMUM_USES),
                latestIssue.capture(),
                latestPreviousUse.capture());

        assertThat(latestIssue.getValue())
                .isEqualTo(now.getValue().minus(FIRST_USE_DELAY));
        assertThat(latestPreviousUse.getValue())
                .isEqualTo(now.getValue().minus(REQUEST_INTERVAL));
    }

    @Test
    @DisplayName("a request with no token is refused without touching the database")
    void shouldRefuseARequestCarryingNoToken() {
        assertThat(visitorTokenService.consumeOneUse(null)).isEqualTo(VisitorTokenOutcome.MISSING);
        assertThat(visitorTokenService.consumeOneUse("  ")).isEqualTo(VisitorTokenOutcome.MISSING);

        verify(visitorTokenRepository, never())
                .consumeOneUse(anyString(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("a token nobody ever issued is reported as unknown")
    void shouldReportAnUnissuedTokenAsUnknown() {
        refuseTheUpdate();
        when(visitorTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThat(visitorTokenService.consumeOneUse("a-token"))
                .isEqualTo(VisitorTokenOutcome.UNKNOWN);
    }

    @Test
    @DisplayName("a token past its lifetime is reported as expired")
    void shouldReportAnOutOfDateTokenAsExpired() {
        refuseTheUpdate();
        storedToken(Instant.now().minusSeconds(1), 0, null);

        assertThat(visitorTokenService.consumeOneUse("a-token"))
                .isEqualTo(VisitorTokenOutcome.EXPIRED);
    }

    @Test
    @DisplayName("a token that has spent its quota is reported as exhausted")
    void shouldReportASpentTokenAsExhausted() {
        refuseTheUpdate();
        storedToken(Instant.now().plusSeconds(600), MAXIMUM_USES, Instant.now().minusSeconds(5));

        assertThat(visitorTokenService.consumeOneUse("a-token"))
                .isEqualTo(VisitorTokenOutcome.EXHAUSTED);
    }

    @Test
    @DisplayName("a healthy token refused by the update was simply used too soon")
    void shouldReportAHealthyButRushedTokenAsTooSoon() {
        refuseTheUpdate();
        storedToken(Instant.now().plusSeconds(600), 3, Instant.now());

        assertThat(visitorTokenService.consumeOneUse("a-token"))
                .isEqualTo(VisitorTokenOutcome.TOO_SOON);
    }

    @Test
    @DisplayName("only being too soon is left uncured by asking for a new token")
    void shouldTellTheClientWhenANewTokenWouldHelp() {
        assertThat(VisitorTokenOutcome.TOO_SOON.isCuredByANewToken()).isFalse();
        assertThat(VisitorTokenOutcome.ACCEPTED.isCuredByANewToken()).isFalse();
        assertThat(VisitorTokenOutcome.MISSING.isCuredByANewToken()).isTrue();
        assertThat(VisitorTokenOutcome.UNKNOWN.isCuredByANewToken()).isTrue();
        assertThat(VisitorTokenOutcome.EXPIRED.isCuredByANewToken()).isTrue();
        assertThat(VisitorTokenOutcome.EXHAUSTED.isCuredByANewToken()).isTrue();
    }

    @Test
    @DisplayName("the throttle can be switched off, and says so")
    void shouldReportWhetherTheThrottleIsEnabled() {
        assertThat(visitorTokenService.isThrottleEnabled()).isTrue();
        assertThat(new VisitorTokenService(visitorTokenRepository, properties(false))
                .isThrottleEnabled()).isFalse();
    }

    @Test
    @DisplayName("a caller told to wait is never told to wait zero seconds")
    void shouldRoundTheRetryDelayUpToAWholeSecond() {
        assertThat(visitorTokenService.retryAfterSeconds()).isEqualTo(1L);
    }

    /**
     * Makes the conditional update decline, which is what forces the service to explain itself.
     */
    private void refuseTheUpdate() {
        when(visitorTokenRepository.consumeOneUse(anyString(), any(), anyInt(), any(), any()))
                .thenReturn(0);
    }

    /**
     * Puts a token in the repository in a state only persistence could otherwise produce.
     *
     * <p>The counters are written by the bulk update in production, so the entity exposes no
     * setters for them; reflection is how a test reproduces a row that has already been used
     * without inventing test-only mutators on a domain object.</p>
     *
     * @param expiresAt  when the stored token expires
     * @param usageCount how many calls it has already made
     * @param lastUsedAt when it was last used, or {@code null} when it never was
     */
    private void storedToken(final Instant expiresAt, final int usageCount, final Instant lastUsedAt) {
        final VisitorTokenEntity token = new VisitorTokenEntity("any-hash", expiresAt);
        ReflectionTestUtils.setField(token, "usageCount", usageCount);
        ReflectionTestUtils.setField(token, "lastUsedAt", lastUsedAt);
        when(visitorTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
    }

    /**
     * Builds the throttle's configuration.
     *
     * @param enabled whether the throttle is switched on
     * @return usable properties
     */
    private static VisitorTokenProperties properties(final boolean enabled) {
        return new VisitorTokenProperties(
                enabled,
                LIFETIME,
                FIRST_USE_DELAY,
                REQUEST_INTERVAL,
                MAXIMUM_USES,
                Duration.ofHours(1));
    }
}
