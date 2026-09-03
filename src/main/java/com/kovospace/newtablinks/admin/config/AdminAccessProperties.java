package com.kovospace.newtablinks.admin.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The operator's way in, bound from {@code newtablinks.admin.*}.
 *
 * <p>This is not an account. There is no row for it, no registration, no password reset and no
 * second factor - one username and one password, supplied to the deployment, that unlock the
 * endpoints under {@code /api/v1/admin}. It exists so that somebody can look at and repair the
 * data when something has gone wrong, which is a thing every real system eventually needs and
 * which no amount of careful API design removes the need for.</p>
 *
 * <p><strong>What that means for the deployment.</strong> This one password is the entire wall
 * between the internet and the ability to read, change and delete any account in the system.
 * Treat it as the most valuable secret this service holds - longer and more random than anything
 * a person would type twice - and give it its own rotation. Everything behind it is irreversible:
 * deleting an account takes every environment, group, subgroup and link it owns with it.</p>
 *
 * <p><strong>It fails closed.</strong> A blank username or password disables the whole admin
 * surface rather than leaving it open, so a deployment that forgets these two variables loses a
 * repair tool instead of publishing an unauthenticated one.</p>
 *
 * @param username                        name the operator signs in with
 * @param password                        password the operator signs in with
 * @param tokenLifetime                   how long an issued admin token stays valid; short on
 *                                        purpose, since it is not refreshable
 * @param maximumConsecutiveFailedAttempts failures in a row after which sign-in is refused
 * @param failedAttemptsLockDuration      how long that refusal lasts
 * @since 0.0.6
 */
@ConfigurationProperties(prefix = "newtablinks.admin")
public record AdminAccessProperties(
        String username,
        String password,
        Duration tokenLifetime,
        int maximumConsecutiveFailedAttempts,
        Duration failedAttemptsLockDuration) {

    /**
     * Rejects a configuration that could not work, at startup rather than per request.
     */
    public AdminAccessProperties {
        if (tokenLifetime == null || tokenLifetime.isZero() || tokenLifetime.isNegative()) {
            throw new IllegalArgumentException(
                    "newtablinks.admin.token-lifetime must be a positive duration");
        }
        if (maximumConsecutiveFailedAttempts <= 0) {
            throw new IllegalArgumentException(
                    "newtablinks.admin.maximum-consecutive-failed-attempts must be positive");
        }
        if (failedAttemptsLockDuration == null || failedAttemptsLockDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "newtablinks.admin.failed-attempts-lock-duration must not be negative");
        }
    }

    /**
     * Tells whether this deployment has an operator sign-in at all.
     *
     * @return {@code true} when both a username and a password are configured
     */
    public boolean isAdministrationEnabled() {
        return username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }
}
