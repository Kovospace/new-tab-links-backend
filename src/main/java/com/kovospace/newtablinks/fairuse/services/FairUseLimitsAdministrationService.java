package com.kovospace.newtablinks.fairuse.services;

import com.kovospace.newtablinks.common.models.PlanLimitValues;
import com.kovospace.newtablinks.fairuse.dtos.FairUseLimitsSaveRequestDto;
import com.kovospace.newtablinks.fairuse.mappers.FairUseLimitsMapper;
import com.kovospace.newtablinks.user.dtos.PlanLimitValuesDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the operator can do to the global Fair Use limits: read them and replace them.
 *
 * <p>A change is not announced to any device: every device reads its account's limits again at
 * its next synchronisation or standing check, and broadcasting to every account at once is
 * exactly the load this avoids.</p>
 *
 * @since 0.0.20
 */
@Service
public class FairUseLimitsAdministrationService {

    private final GlobalFairUseLimitsService globalFairUseLimitsService;
    private final FairUseLimitsMapper fairUseLimitsMapper;

    /**
     * Creates the service.
     *
     * @param globalFairUseLimitsService the global Fair Use limits
     * @param fairUseLimitsMapper        converts limits for the client
     */
    public FairUseLimitsAdministrationService(
            final GlobalFairUseLimitsService globalFairUseLimitsService,
            final FairUseLimitsMapper fairUseLimitsMapper) {

        this.globalFairUseLimitsService = globalFairUseLimitsService;
        this.fairUseLimitsMapper = fairUseLimitsMapper;
    }

    /**
     * Reads the global Fair Use limits as stored right now.
     *
     * @return the global limits
     */
    @Transactional(readOnly = true)
    public PlanLimitValuesDto findGlobalFairUseLimits() {
        return fairUseLimitsMapper.toDto(globalFairUseLimitsService.readStoredFairUseLimits());
    }

    /**
     * Replaces the global Fair Use limits. Lowering one is allowed.
     *
     * @param saveRequest the new limits, already validated as present and at least one each
     * @return the limits as stored
     */
    @Transactional
    public PlanLimitValuesDto replaceGlobalFairUseLimits(
            final FairUseLimitsSaveRequestDto saveRequest) {

        return fairUseLimitsMapper.toDto(
                globalFairUseLimitsService.replaceFairUseLimits(toPlanLimitValues(saveRequest)));
    }

    /**
     * Turns a validated request into a set of limits.
     *
     * @param saveRequest the request, every field present
     * @return the limits it asks for
     */
    private static PlanLimitValues toPlanLimitValues(final FairUseLimitsSaveRequestDto saveRequest) {
        return new PlanLimitValues(
                saveRequest.profiles(),
                saveRequest.workspacesPerProfile(),
                saveRequest.groupsPerWorkspace(),
                saveRequest.subgroupsPerGroup(),
                saveRequest.linksPerWorkspace(),
                saveRequest.closedTabs(),
                saveRequest.browsingHistory(),
                saveRequest.devices());
    }
}
