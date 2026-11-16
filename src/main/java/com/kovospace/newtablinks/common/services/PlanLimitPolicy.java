package com.kovospace.newtablinks.common.services;

import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.common.config.PlanLimitProperties;
import com.kovospace.newtablinks.common.exceptions.PlanLimitReachedException;
import com.kovospace.newtablinks.common.models.EffectivePlanLimits;
import com.kovospace.newtablinks.common.models.PlanLimit;
import com.kovospace.newtablinks.common.models.PlanLimitRefusalCode;
import com.kovospace.newtablinks.common.models.PlanLimitValues;
import com.kovospace.newtablinks.entitlement.services.EntitlementStandingService;
import com.kovospace.newtablinks.fairuse.services.AccountFairUseLimitsService;
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
 * <p>The Fair Use half is the account's own ({@link AccountFairUseLimitsService}): the global
 * Fair Use limits the operator sets, raised wherever the operator raised them for this account.
 * A free account gets the free plan's configured numbers laid over that set.</p>
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
    private final AccountFairUseLimitsService accountFairUseLimitsService;
    private final WebApplicationProperties webApplicationProperties;

    /**
     * Creates the policy.
     *
     * @param planLimitProperties         the free plan's own limits
     * @param entitlementStandingService  tells whether an account is premium right now
     * @param accountFairUseLimitsService the Fair Use limits of one account
     * @param webApplicationProperties    where the website's devices page is
     */
    public PlanLimitPolicy(
            final PlanLimitProperties planLimitProperties,
            final EntitlementStandingService entitlementStandingService,
            final AccountFairUseLimitsService accountFairUseLimitsService,
            final WebApplicationProperties webApplicationProperties) {

        this.planLimitProperties = planLimitProperties;
        this.entitlementStandingService = entitlementStandingService;
        this.accountFairUseLimitsService = accountFairUseLimitsService;
        this.webApplicationProperties = webApplicationProperties;
    }

    /**
     * Returns the limits that hold for an account at this moment.
     *
     * @param ownerId identifier of the account
     * @return the account's Fair Use set when it is premium now, otherwise that set with the
     *         free plan's own numbers laid over it
     */
    public EffectivePlanLimits effectiveLimitsFor(final UUID ownerId) {
        final boolean premium = entitlementStandingService.isAccountProNow(ownerId);
        final PlanLimitValues fairUseLimits = accountFairUseLimitsService.fairUseLimitsFor(ownerId);
        return new EffectivePlanLimits(premium, premium
                ? fairUseLimits
                : planLimitProperties.free().overlayOn(fairUseLimits), fairUseLimits);
    }

    /**
     * Builds the refusal of a request that one limit does not allow.
     *
     * <p>The code is {@link PlanLimitRefusalCode#FREE_PLAN_LIMIT_REACHED} exactly when the account
     * is free and its free limit is below its own Fair Use one - when upgrading would have let
     * the request through. Every other refusal is the Fair Use Policy's.</p>
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
                && maximum < limits.fairUse().maximumFor(limit);
        final PlanLimitRefusalCode code = upgradeWouldHelp
                ? PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED
                : PlanLimitRefusalCode.FAIR_USE_LIMIT_REACHED;

        LOGGER.info("Refused a request past the plan limit {} of {} ({})", limit, maximum, code);
        return new PlanLimitReachedException(
                code, limit, maximum, webApplicationProperties.buildDevicesPageLink());
    }
}
