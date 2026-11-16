package com.kovospace.newtablinks.fairuse.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.common.config.PlanLimitProperties;
import com.kovospace.newtablinks.common.exceptions.PlanLimitReachedException;
import com.kovospace.newtablinks.common.models.EffectivePlanLimits;
import com.kovospace.newtablinks.common.models.FreePlanLimitValues;
import com.kovospace.newtablinks.common.models.PlanLimit;
import com.kovospace.newtablinks.common.models.PlanLimitRefusalCode;
import com.kovospace.newtablinks.common.models.PlanLimitValues;
import com.kovospace.newtablinks.common.services.PlanLimitPolicy;
import com.kovospace.newtablinks.entitlement.services.EntitlementStandingService;
import com.kovospace.newtablinks.fairuse.models.FairUseLimitOverrides;
import com.kovospace.newtablinks.fairuse.models.UserFairUseOverrideEntity;
import com.kovospace.newtablinks.fairuse.repositories.UserFairUseOverrideRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins how {@link PlanLimitPolicy} resolves an account's limits once the Fair Use half is the
 * account's own: the global limits, raised by its override, with the free plan's numbers over
 * them for an account that is not premium - and a refusal judged against that same set.
 */
class PlanLimitPolicyWithFairUseOverridesTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static final PlanLimitValues GLOBAL_FAIR_USE =
            new PlanLimitValues(50, 50, 25, 25, 500, 500, 1000, 100);

    private static final FreePlanLimitValues FREE_PLAN = new FreePlanLimitValues(1, 2, 25, 50, 5);

    private final EntitlementStandingService entitlementStandingService =
            mock(EntitlementStandingService.class);
    private final GlobalFairUseLimitsService globalFairUseLimitsService =
            mock(GlobalFairUseLimitsService.class);
    private final UserFairUseOverrideRepository userFairUseOverrideRepository =
            mock(UserFairUseOverrideRepository.class);

    private final PlanLimitPolicy planLimitPolicy = new PlanLimitPolicy(
            new PlanLimitProperties(GLOBAL_FAIR_USE, FREE_PLAN, Duration.ofSeconds(30)),
            entitlementStandingService,
            new AccountFairUseLimitsService(globalFairUseLimitsService, userFairUseOverrideRepository),
            new WebApplicationProperties("https://tabilinks.example", null, null, null, null,
                    "/devices"));

    @Test
    @DisplayName("a premium account gets the global Fair Use limits raised by its override")
    void shouldGiveAPremiumAccountItsRaisedFairUseLimits() {
        givenTheAccountIsPremium(true);
        givenTheGlobalFairUseLimits(GLOBAL_FAIR_USE);
        givenTheOverride(new FairUseLimitOverrides(80, null, null, null, 2000, null, null, null));

        final EffectivePlanLimits limits = planLimitPolicy.effectiveLimitsFor(ACCOUNT_ID);

        assertThat(limits.values())
                .isEqualTo(new PlanLimitValues(80, 50, 25, 25, 2000, 500, 1000, 100));
        assertThat(limits.fairUse()).isEqualTo(limits.values());
    }

    @Test
    @DisplayName("a free account gets the free plan's numbers over its raised Fair Use limits")
    void shouldLayTheFreePlanOverTheRaisedFairUseLimits() {
        givenTheAccountIsPremium(false);
        givenTheGlobalFairUseLimits(GLOBAL_FAIR_USE);
        givenTheOverride(new FairUseLimitOverrides(80, null, null, null, 2000, null, null, null));

        final EffectivePlanLimits limits = planLimitPolicy.effectiveLimitsFor(ACCOUNT_ID);

        assertThat(limits.values()).isEqualTo(new PlanLimitValues(1, 2, 25, 25, 2000, 25, 50, 5));
        assertThat(limits.fairUse().profiles()).isEqualTo(80);
    }

    @Test
    @DisplayName("the global limits the operator stored hold, not the configured ones")
    void shouldUseTheStoredGlobalLimitsRatherThanTheConfiguredOnes() {
        givenTheAccountIsPremium(true);
        givenTheGlobalFairUseLimits(new PlanLimitValues(10, 10, 5, 5, 100, 100, 200, 20));

        assertThat(planLimitPolicy.effectiveLimitsFor(ACCOUNT_ID).maximumFor(PlanLimit.PROFILES))
                .isEqualTo(10);
    }

    @Test
    @DisplayName("a refusal at the raised limit is the Fair Use Policy's, carrying that maximum")
    void shouldReportTheRaisedMaximumInAFairUseRefusal() {
        givenTheAccountIsPremium(true);
        givenTheGlobalFairUseLimits(GLOBAL_FAIR_USE);
        givenTheOverride(new FairUseLimitOverrides(null, null, null, null, 2000, null, null, null));

        final PlanLimitReachedException refusal = planLimitPolicy.refusalOf(
                planLimitPolicy.effectiveLimitsFor(ACCOUNT_ID), PlanLimit.LINKS_PER_WORKSPACE);

        assertThat(refusal.getCode()).isEqualTo(PlanLimitRefusalCode.FAIR_USE_LIMIT_REACHED);
        assertThat(refusal.getMaximum()).isEqualTo(2000);
    }

    @Test
    @DisplayName("a free account's refusal is the free plan's only when its own Fair Use limit "
            + "is higher")
    void shouldCompareAFreeRefusalWithTheAccountsOwnFairUseLimit() {
        givenTheAccountIsPremium(false);
        givenTheGlobalFairUseLimits(new PlanLimitValues(1, 50, 25, 25, 500, 500, 1000, 100));

        final EffectivePlanLimits withoutOverride = planLimitPolicy.effectiveLimitsFor(ACCOUNT_ID);
        assertThat(planLimitPolicy.refusalOf(withoutOverride, PlanLimit.PROFILES).getCode())
                .isEqualTo(PlanLimitRefusalCode.FAIR_USE_LIMIT_REACHED);

        givenTheOverride(new FairUseLimitOverrides(3, null, null, null, null, null, null, null));
        final EffectivePlanLimits withOverride = planLimitPolicy.effectiveLimitsFor(ACCOUNT_ID);
        assertThat(planLimitPolicy.refusalOf(withOverride, PlanLimit.PROFILES).getCode())
                .isEqualTo(PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED);
    }

    /**
     * Sets the account's standing.
     *
     * @param premium whether it is premium
     */
    private void givenTheAccountIsPremium(final boolean premium) {
        when(entitlementStandingService.isAccountProNow(ACCOUNT_ID)).thenReturn(premium);
    }

    /**
     * Sets the global Fair Use limits in force.
     *
     * @param limits the limits
     */
    private void givenTheGlobalFairUseLimits(final PlanLimitValues limits) {
        when(globalFairUseLimitsService.currentFairUseLimits()).thenReturn(limits);
    }

    /**
     * Gives the account an override.
     *
     * @param overrides the raised limits
     */
    private void givenTheOverride(final FairUseLimitOverrides overrides) {
        final UserFairUseOverrideEntity override = new UserFairUseOverrideEntity(ACCOUNT_ID);
        override.replaceWith(overrides, null);
        when(userFairUseOverrideRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(override));
    }
}
