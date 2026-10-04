package com.kovospace.newtablinks.user.mappers;

import com.kovospace.newtablinks.common.models.EffectivePlanLimits;
import com.kovospace.newtablinks.user.dtos.PlanLimitsDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converts an account's {@link EffectivePlanLimits} into the shape returned to clients.
 *
 * @since 0.0.18
 */
@Mapper
public interface PlanLimitsMapper {

    /**
     * Converts the limits.
     *
     * @param effectivePlanLimits the limits that hold for the account
     * @return the converted limits
     */
    @Mapping(target = "limits", source = "values")
    PlanLimitsDto toDto(EffectivePlanLimits effectivePlanLimits);
}
