package com.kovospace.newtablinks.user.services;

import com.kovospace.newtablinks.common.services.PlanLimitPolicy;
import com.kovospace.newtablinks.user.dtos.PlanLimitsDto;
import com.kovospace.newtablinks.user.mappers.PlanLimitsMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells a client which limits hold for its account right now.
 *
 * @since 0.0.18
 */
@Service
public class AccountPlanLimitsService {

    private final PlanLimitPolicy planLimitPolicy;
    private final PlanLimitsMapper planLimitsMapper;

    /**
     * Creates the service.
     *
     * @param planLimitPolicy  decides the limits
     * @param planLimitsMapper converts them for the client
     */
    public AccountPlanLimitsService(
            final PlanLimitPolicy planLimitPolicy,
            final PlanLimitsMapper planLimitsMapper) {

        this.planLimitPolicy = planLimitPolicy;
        this.planLimitsMapper = planLimitsMapper;
    }

    /**
     * Returns the limits that hold for an account now, judged from its live entitlement.
     *
     * @param userId identifier of the account
     * @return its standing and limits
     */
    @Transactional(readOnly = true)
    public PlanLimitsDto findPlanLimitsOf(final UUID userId) {
        return planLimitsMapper.toDto(planLimitPolicy.effectiveLimitsFor(userId));
    }
}
