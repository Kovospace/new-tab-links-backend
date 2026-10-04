package com.kovospace.newtablinks.common.config;

import com.kovospace.newtablinks.common.models.FreePlanLimitValues;
import com.kovospace.newtablinks.common.models.PlanLimitValues;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every plan limit, bound from {@code newtablinks.plan-limits.*} - the one place the numbers live.
 *
 * <p>The defaults in {@code application.properties} <em>are</em> the published plans - the
 * website's price table and its Fair Use Policy - so a deployment sets none of them. The
 * variables exist so that a change of plan is a configuration change, not a release of the
 * extension, which reads the effective numbers from {@code GET /api/v1/users/me/plan-limits}.</p>
 *
 * @param premium the Fair Use Policy - every limit of a premium account, and the free plan's too
 *                wherever it names none of its own
 * @param free    the free plan's own, lower limits
 * @since 0.0.18
 */
@ConfigurationProperties(prefix = "newtablinks.plan-limits")
public record PlanLimitProperties(PlanLimitValues premium, FreePlanLimitValues free) {

    /**
     * Returns the free plan's complete set of limits.
     *
     * @return the premium set with the free plan's own numbers in place
     */
    public PlanLimitValues freeLimits() {
        return free.overlayOn(premium);
    }
}
