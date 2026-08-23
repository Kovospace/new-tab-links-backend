package com.kovospace.newtablinks.subgroup.mappers;

import com.kovospace.newtablinks.subgroup.dtos.SubgroupDto;
import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converts {@link SubgroupEntity} into the shape returned to clients.
 *
 * <p>Mapping is one-directional; see {@link com.kovospace.newtablinks.user.mappers.UserMapper}
 * for why.</p>
 *
 * @since 0.0.1
 */
@Mapper
public interface SubgroupMapper {

    /**
     * Converts a single subgroup, flattening the parent group to its identifier.
     *
     * @param subgroupEntity entity to convert
     * @return the converted subgroup
     */
    @Mapping(target = "parentGroupId", source = "parentGroup.id")
    SubgroupDto toDto(SubgroupEntity subgroupEntity);

    /**
     * Converts a list of subgroups, preserving order.
     *
     * @param subgroupEntities entities to convert
     * @return the converted subgroups, empty when the input is empty
     */
    List<SubgroupDto> toDtoList(List<SubgroupEntity> subgroupEntities);
}
