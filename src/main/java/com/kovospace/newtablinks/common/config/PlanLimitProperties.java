package com.kovospace.newtablinks.common.config;

import com.kovospace.newtablinks.common.models.FreePlanLimitValues;
import com.kovospace.newtablinks.common.models.PlanLimitValues;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every plan limit, bound from {@code newtablinks.plan-limits.*}.
 *
 * <p>The free plan's numbers live only here. The Fair Use Policy's ({@code premium}) are the
 * <em>fallback</em> since 0.0.20: the operator can change them, and the values in force are the
 * {@code fair_use_limits} row, which the migration seeded with these defaults (see
 * {@code GlobalFairUseLimitsService}). These are used only while that row is missing.</p>
 *
 * <p>The defaults in {@code application.properties} <em>are</em> the published plans - the
 * website's price table and its Fair Use Policy - so a deployment sets none of them. The
 * variables exist so that a change of plan is a configuration change, not a release of the
 * extension, which reads the effective numbers from {@code GET /api/v1/users/me/plan-limits}.</p>
 *
 * @param premium                the Fair Use Policy as configured - used only when the
 *                               {@code fair_use_limits} row is missing
 * @param free                   the free plan's own, lower limits
 * @param fairUseCacheLifetime   how long one replica keeps the global Fair Use limits it read
 *                               before reading them again; an operator's change made on another
 *                               replica reaches this one within it. Since 0.0.20.
 * @since 0.0.18
 */
@ConfigurationProperties(prefix = "newtablinks.plan-limits")
public record PlanLimitProperties(
        PlanLimitValues premium,
        FreePlanLimitValues free,
        Duration fairUseCacheLifetime) {
}
