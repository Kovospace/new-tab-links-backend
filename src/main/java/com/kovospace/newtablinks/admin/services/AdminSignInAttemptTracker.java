package com.kovospace.newtablinks.admin.services;

import com.kovospace.newtablinks.admin.config.AdminAccessProperties;
import com.kovospace.newtablinks.admin.models.AdminSignInLockEntity;
import com.kovospace.newtablinks.admin.repositories.AdminSignInLockRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts failed operator sign-ins and refuses to listen for a while once there have been too many.
 *
 * <p>The admin credentials are a single fixed pair with no account behind them, so the
 * per-account failed-login counter that protects ordinary users cannot protect this. Without
 * something here, the one password guarding every account in the system would be open to
 * unlimited guessing at network speed.</p>
 *
 * <p><strong>One counter, not one per presented username.</strong> Keying by whatever the caller
 * typed would let anyone grow the count without bound by sending a new username every time, and
 * it would buy nothing: there is exactly one admin username and it is not a secret worth
 * protecting. The cost is that a wrong guess at <em>any</em> username delays the real operator
 * too - which is the accepted trade of every lockout, and the reason the lock is minutes rather
 * than hours.</p>
 *
 * <p><strong>Kept in the database, so every replica shares it.</strong> It used to be two fields
 * on this bean, which meant one counter per pod: two replicas gave an attacker twice the guesses
 * before either locked, and a restart forgot them all. {@link AdminSignInLockRepository} changes
 * the one row in single statements, which the database serialises across pods.</p>
 *
 * @since 0.0.6
 */
@Component
public class AdminSignInAttemptTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminSignInAttemptTracker.class);

    private final AdminAccessProperties adminAccessProperties;
    private final AdminSignInLockRepository signInLockRepository;

    /**
     * Creates the tracker.
     *
     * @param adminAccessProperties how many failures are allowed and for how long they lock
     * @param signInLockRepository  where the count lives, shared by every replica
     */
    public AdminSignInAttemptTracker(
            final AdminAccessProperties adminAccessProperties,
            final AdminSignInLockRepository signInLockRepository) {

        this.adminAccessProperties = adminAccessProperties;
        this.signInLockRepository = signInLockRepository;
    }

    /**
     * How much longer sign-in stays refused.
     *
     * <p>Checked before the credentials are looked at, so that a locked-out caller learns nothing
     * about whether the pair they sent was right. A lock that has run out is forgotten here,
     * failures and all.</p>
     *
     * @return the remaining lock, or {@link Duration#ZERO} when sign-in may be attempted
     */
    @Transactional
    public Duration remainingLock() {
        signInLockRepository.releaseExpiredLock();
        return signInLockRepository.findRemainingLockMilliseconds()
                .map(Duration::ofMillis)
                .orElse(Duration.ZERO);
    }

    /**
     * Records a failed attempt, locking sign-in when there have been too many.
     */
    @Transactional
    public void recordFailure() {
        signInLockRepository.recordFailure(
                adminAccessProperties.maximumConsecutiveFailedAttempts(),
                adminAccessProperties.failedAttemptsLockDuration().toMillis());
        signInLockRepository.findById(AdminSignInLockEntity.SINGLE_ROW_ID)
                .ifPresent(this::logFailure);
    }

    /**
     * Forgets every failure, because the operator has just proved who they are.
     */
    @Transactional
    public void recordSuccess() {
        signInLockRepository.clearFailures();
    }

    /**
     * Says how close the failures are to the lock, or that they reached it.
     *
     * @param signInLock the row as the failure left it
     */
    private void logFailure(final AdminSignInLockEntity signInLock) {
        if (signInLock.getLockedUntil() != null) {
            LOGGER.warn("Admin sign-in locked until {} after {} consecutive failures",
                    signInLock.getLockedUntil(), signInLock.getConsecutiveFailures());
            return;
        }
        LOGGER.warn("Admin sign-in failed ({} of {} before the lock)",
                signInLock.getConsecutiveFailures(),
                adminAccessProperties.maximumConsecutiveFailedAttempts());
    }
}
