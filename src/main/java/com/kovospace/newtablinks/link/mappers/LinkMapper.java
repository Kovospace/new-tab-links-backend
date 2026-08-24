package com.kovospace.newtablinks.link.mappers;

import com.kovospace.newtablinks.link.dtos.LinkDto;
import com.kovospace.newtablinks.link.models.LinkEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converts {@link LinkEntity} into the shape returned to clients.
 *
 * <p>Mapping is one-directional; see {@link com.kovospace.newtablinks.user.mappers.UserMapper}
 * for why.</p>
 *
 * @since 0.0.1
 */
@Mapper
public interface LinkMapper {

    /**
     * Converts a single link, flattening both parents to their identifiers.
     *
     * <p>A link with no subgroup maps to a {@code null} {@code parentSubgroupId}.</p>
     *
     * @param linkEntity entity to convert
     * @return the converted link
     */
    @Mapping(target = "parentGroupId", source = "parentGroup.id")
    @Mapping(target = "parentSubgroupId", source = "parentSubgroup.id")
    LinkDto toDto(LinkEntity linkEntity);

    /**
     * Converts a list of links, preserving order.
     *
     * @param linkEntities entities to convert
     * @return the converted links, empty when the input is empty
     */
    List<LinkDto> toDtoList(List<LinkEntity> linkEntities);
}
