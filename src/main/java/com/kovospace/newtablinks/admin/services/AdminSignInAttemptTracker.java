package com.kovospace.newtablinks.admin.services;

import com.kovospace.newtablinks.admin.config.AdminAccessProperties;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Counts failed operator sign-ins and refuses to listen for a while once there have been too many.
 *
 * <p>The admin credentials are a single fixed pair with no account behind them, so the
 * per-account failed-login counter that protects ordinary users cannot protect this. Without
 * something here, the one password guarding every account in the system would be open to
 * unlimited guessing at network speed.</p>
 *
 * <p><strong>One counter, not one per presented username.</strong> Keying by whatever the caller
 * typed would let anyone grow this map without bound by sending a new username every time, and it
 * would buy nothing: there is exactly one admin username and it is not a secret worth protecting.
 * The cost is that a wrong guess at <em>any</em> username delays the real operator too - which is
 * the accepted trade of every lockout, and the reason the lock is minutes rather than hours.</p>
 *
 * <p><strong>Held in memory, so it is per replica.</strong> Two pods mean two counters and twice
 * the guesses before either locks. That matches how this service already runs - its websocket
 * broker is single-replica only - but it is the thing to fix first if this ever scales out:
 * move the counter to the database, where the rest of the security state already lives.</p>
 *
 * @since 0.0.6
 */
@Component
public class AdminSignInAttemptTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminSignInAttemptTracker.class);

    private final AdminAccessProperties adminAccessProperties;

    /** Consecutive failures since the last success or the last expiry of a lock. */
    private int consecutiveFailures;

    /** Moment the current lock lifts, or {@code null} when sign-in is not locked. */
    private Instant lockedUntil;

    /**
     * Creates the tracker.
     *
     * @param adminAccessProperties how many failures are allowed and for how long they lock
     */
    public AdminSignInAttemptTracker(final AdminAccessProperties adminAccessProperties) {
        this.adminAccessProperties = adminAccessProperties;
    }

    /**
     * How much longer sign-in stays refused.
     *
     * <p>Checked before the credentials are looked at, so that a locked-out caller learns nothing
     * about whether the pair they sent was right.</p>
     *
     * @return the remaining lock, or {@link Duration#ZERO} when sign-in may be attempted
     */
    public synchronized Duration remainingLock() {
        if (lockedUntil == null) {
            return Duration.ZERO;
        }

        final Duration remaining = Duration.between(Instant.now(), lockedUntil);
        if (remaining.isNegative() || remaining.isZero()) {
            // The lock has served its time. Forgetting the failures with it is what makes this a
            // delay rather than a permanent lockout nobody can clear without a restart.
            lockedUntil = null;
            consecutiveFailures = 0;
            return Duration.ZERO;
        }
        return remaining;
    }

    /**
     * Records a failed attempt, locking sign-in when there have been too many.
     */
    public synchronized void recordFailure() {
        consecutiveFailures++;

        if (consecutiveFailures >= adminAccessProperties.maximumConsecutiveFailedAttempts()) {
            lockedUntil = Instant.now().plus(adminAccessProperties.failedAttemptsLockDuration());
            LOGGER.warn("Admin sign-in locked until {} after {} consecutive failures",
                    lockedUntil, consecutiveFailures);
        } else {
            LOGGER.warn("Admin sign-in failed ({} of {} before the lock)",
                    consecutiveFailures, adminAccessProperties.maximumConsecutiveFailedAttempts());
        }
    }

    /**
     * Forgets every failure, because the operator has just proved who they are.
     */
    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        lockedUntil = null;
    }
}
