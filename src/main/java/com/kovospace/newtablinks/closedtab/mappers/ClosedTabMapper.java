package com.kovospace.newtablinks.closedtab.mappers;

import com.kovospace.newtablinks.closedtab.dtos.ClosedTabDto;
import com.kovospace.newtablinks.closedtab.models.ClosedTabEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converts {@link ClosedTabEntity} into the shape returned to clients.
 *
 * <p>Mapping is one-directional; see {@link com.kovospace.newtablinks.user.mappers.UserMapper}
 * for why.</p>
 *
 * @since 0.0.8
 */
@Mapper
public interface ClosedTabMapper {

    /**
     * Converts a single closed tab, flattening its profile to an identifier.
     *
     * @param closedTabEntity entity to convert
     * @return the converted closed tab
     */
    @Mapping(target = "profileId", source = "profile.id")
    ClosedTabDto toDto(ClosedTabEntity closedTabEntity);

    /**
     * Converts a list of closed tabs, preserving order.
     *
     * @param closedTabEntities entities to convert
     * @return the converted closed tabs, empty when the input is empty
     */
    List<ClosedTabDto> toDtoList(List<ClosedTabEntity> closedTabEntities);
}
