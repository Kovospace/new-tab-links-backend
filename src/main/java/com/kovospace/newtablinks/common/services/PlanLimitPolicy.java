package com.kovospace.newtablinks.common.services;

import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.common.config.PlanLimitProperties;
import com.kovospace.newtablinks.common.exceptions.PlanLimitReachedException;
import com.kovospace.newtablinks.common.models.EffectivePlanLimits;
import com.kovospace.newtablinks.common.models.PlanLimit;
import com.kovospace.newtablinks.common.models.PlanLimitRefusalCode;
import com.kovospace.newtablinks.entitlement.services.EntitlementStandingService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decides which limits hold for an account right now, and how a refusal of one is worded.
 *
 * <p>The single answer to "free or premium" for every limit check: premium is the account's
 * entitlement at this moment ({@link EntitlementStandingService#isAccountProNow(UUID)}), never a
 * stored flag, so an account whose subscription lapsed is held to the free plan from that moment
 * on and a renewed one is released at once.</p>
 *
 * <p>Not transactional itself and never throws: it builds the refusal and leaves throwing to the
 * caller, so that a caller whose transaction must survive the refusal (the token refresh, which
 * revokes before it refuses) controls the rollback rules alone.</p>
 *
 * @since 0.0.18
 */
@Service
public class PlanLimitPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanLimitPolicy.class);

    private final PlanLimitProperties planLimitProperties;
    private final EntitlementStandingService entitlementStandingService;
    private final WebApplicationProperties webApplicationProperties;

    /**
     * Creates the policy.
     *
     * @param planLimitProperties        both plans' limits
     * @param entitlementStandingService tells whether an account is premium right now
     * @param webApplicationProperties   where the website's devices page is
     */
    public PlanLimitPolicy(
            final PlanLimitProperties planLimitProperties,
            final EntitlementStandingService entitlementStandingService,
            final WebApplicationProperties webApplicationProperties) {

        this.planLimitProperties = planLimitProperties;
        this.entitlementStandingService = entitlementStandingService;
        this.webApplicationProperties = webApplicationProperties;
    }

    /**
     * Returns the limits that hold for an account at this moment.
     *
     * @param ownerId identifier of the account
     * @return the premium set when the account is premium now, otherwise the free set
     */
    public EffectivePlanLimits effectiveLimitsFor(final UUID ownerId) {
        final boolean premium = entitlementStandingService.isAccountProNow(ownerId);
        return new EffectivePlanLimits(premium, premium
                ? planLimitProperties.premium()
                : planLimitProperties.freeLimits());
    }

    /**
     * Builds the refusal of a request that one limit does not allow.
     *
     * <p>The code is {@link PlanLimitRefusalCode#FREE_PLAN_LIMIT_REACHED} exactly when the account
     * is free and its free limit is below the premium one - when upgrading would have let the
     * request through. Every other refusal is the Fair Use Policy's.</p>
     *
     * @param limits the limits the request was judged against
     * @param limit  the limit it would have exceeded
     * @return the exception to throw, carrying the code, the maximum and the devices page
     */
    public PlanLimitReachedException refusalOf(
            final EffectivePlanLimits limits,
            final PlanLimit limit) {

        final int maximum = limits.maximumFor(limit);
        final boolean upgradeWouldHelp = !limits.premium()
                && maximum < planLimitProperties.premium().maximumFor(limit);
        final PlanLimitRefusalCode code = upgradeWouldHelp
                ? PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED
                : PlanLimitRefusalCode.FAIR_USE_LIMIT_REACHED;

        LOGGER.info("Refused a request past the plan limit {} of {} ({})", limit, maximum, code);
        return new PlanLimitReachedException(
                code, limit, maximum, webApplicationProperties.buildDevicesPageLink());
    }
}
