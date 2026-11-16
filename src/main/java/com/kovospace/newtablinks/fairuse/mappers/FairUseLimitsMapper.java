package com.kovospace.newtablinks.fairuse.mappers;

import com.kovospace.newtablinks.common.models.PlanLimitValues;
import com.kovospace.newtablinks.fairuse.models.FairUseLimitOverrides;
import com.kovospace.newtablinks.fairuse.dtos.FairUseLimitOverridesDto;
import com.kovospace.newtablinks.user.dtos.PlanLimitValuesDto;
import org.mapstruct.Mapper;

/**
 * Converts Fair Use limits and overrides into the shapes returned to clients.
 *
 * @since 0.0.20
 */
@Mapper
public interface FairUseLimitsMapper {

    /**
     * Converts a full set of limits.
     *
     * @param planLimitValues the limits
     * @return the converted limits
     */
    PlanLimitValuesDto toDto(PlanLimitValues planLimitValues);

    /**
     * Converts an account's overrides.
     *
     * @param fairUseLimitOverrides the overrides
     * @return the converted overrides, {@code null} where a limit is not raised
     */
    FairUseLimitOverridesDto toDto(FairUseLimitOverrides fairUseLimitOverrides);
}
