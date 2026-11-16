package com.kovospace.newtablinks.fairuse.services;

import com.kovospace.newtablinks.common.config.PlanLimitProperties;
import com.kovospace.newtablinks.common.models.PlanLimitValues;
import com.kovospace.newtablinks.fairuse.dtos.PublishedPlanLimitsDto;
import com.kovospace.newtablinks.fairuse.mappers.FairUseLimitsMapper;
import org.springframework.stereotype.Service;

/**
 * The plans' limits as published to anybody: the free plan's and the global Fair Use Policy's.
 *
 * <p>No account is involved, so no per-account raise is either. Served from this replica's cached
 * copy of the global limits, which is what makes it cheap enough to answer every visitor.</p>
 *
 * @since 0.0.20
 */
@Service
public class PublishedPlanLimitsService {

    private final GlobalFairUseLimitsService globalFairUseLimitsService;
    private final PlanLimitProperties planLimitProperties;
    private final FairUseLimitsMapper fairUseLimitsMapper;

    /**
     * Creates the service.
     *
     * @param globalFairUseLimitsService the global Fair Use limits
     * @param planLimitProperties        the free plan's own limits
     * @param fairUseLimitsMapper        converts limits for the client
     */
    public PublishedPlanLimitsService(
            final GlobalFairUseLimitsService globalFairUseLimitsService,
            final PlanLimitProperties planLimitProperties,
            final FairUseLimitsMapper fairUseLimitsMapper) {

        this.globalFairUseLimitsService = globalFairUseLimitsService;
        this.planLimitProperties = planLimitProperties;
        this.fairUseLimitsMapper = fairUseLimitsMapper;
    }

    /**
     * Returns both plans' limits.
     *
     * @return the free plan's limits (its own numbers over the global Fair Use ones) and the
     *         global Fair Use limits
     */
    public PublishedPlanLimitsDto findPublishedPlanLimits() {
        final PlanLimitValues fairUseLimits = globalFairUseLimitsService.currentFairUseLimits();
        return new PublishedPlanLimitsDto(
                fairUseLimitsMapper.toDto(planLimitProperties.free().overlayOn(fairUseLimits)),
                fairUseLimitsMapper.toDto(fairUseLimits));
    }
}
