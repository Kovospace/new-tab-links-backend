package com.kovospace.newtablinks.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The throttle applied to the endpoints an anonymous visitor may call, bound from
 * {@code newtablinks.visitor-token.*}.
 *
 * <p>Two endpoints answer whether a username is registered - the lookup that serves the
 * registration form while somebody types, and registration itself, which refuses a taken name
 * with 409. Both are therefore account enumeration oracles, and neither can be closed without
 * losing the feature. What can be taken away is the <em>speed</em>: a caller must first ask for a
 * token, then use it no faster than a person types, and only so many times before asking for
 * another.</p>
 *
 * <p><strong>What this does and does not buy.</strong> Issuing a token is free and unauthenticated
 * - it has to be, the visitor is anonymous - so a determined caller can hold many tokens at once
 * and regain throughput by running them in parallel. This is a rate limit per token, not a proof
 * that a human is present. What it removes is the cheap case: a single script that walks a word
 * list as fast as the network allows. What it costs a real visitor is nothing, because the
 * website already debounces the same keystrokes.</p>
 *
 * <p>It is deliberately <strong>not</strong> a per-address limit. Whole neighbourhoods share one
 * address behind carrier-grade NAT, so counting by address punishes real users in numbers while
 * barely inconveniencing anyone with a handful of addresses to rotate through.</p>
 *
 * @param enabled                whether the guarded endpoints demand a token at all; false lets
 *                               every call through and exists for local work and tests
 * @param lifetime               how long a freshly issued token stays usable
 * @param minimumFirstUseDelay   how long after issue the first call must wait, which rejects a
 *                               caller that asks for a token and spends it in the same breath
 * @param minimumRequestInterval shortest gap allowed between two calls made with one token; keep
 *                               it below the website's own debounce or real typing will be refused
 * @param maximumUses            how many calls one token may make before it is spent
 * @param cleanupInterval        how often expired rows are deleted
 * @since 0.0.5
 */
@ConfigurationProperties(prefix = "newtablinks.visitor-token")
public record VisitorTokenProperties(
        boolean enabled,
        Duration lifetime,
        Duration minimumFirstUseDelay,
        Duration minimumRequestInterval,
        int maximumUses,
        Duration cleanupInterval) {

    /**
     * Rejects a configuration that could not work, at startup rather than per request.
     *
     * <p>A non-positive lifetime or use count would refuse every call while looking configured,
     * and a negative delay would silently mean "no delay". Failing to boot is the honest answer
     * to all three.</p>
     */
    public VisitorTokenProperties {
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException(
                    "newtablinks.visitor-token.lifetime must be a positive duration");
        }
        if (minimumFirstUseDelay == null || minimumFirstUseDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "newtablinks.visitor-token.minimum-first-use-delay must not be negative");
        }
        if (minimumRequestInterval == null || minimumRequestInterval.isNegative()) {
            throw new IllegalArgumentException(
                    "newtablinks.visitor-token.minimum-request-interval must not be negative");
        }
        if (maximumUses <= 0) {
            throw new IllegalArgumentException(
                    "newtablinks.visitor-token.maximum-uses must be positive");
        }
        if (cleanupInterval == null || cleanupInterval.isZero() || cleanupInterval.isNegative()) {
            throw new IllegalArgumentException(
                    "newtablinks.visitor-token.cleanup-interval must be a positive duration");
        }
    }
}
