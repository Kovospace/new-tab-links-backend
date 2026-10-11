package com.kovospace.newtablinks.visit.mappers;

import com.kovospace.newtablinks.visit.dtos.VisitDto;
import com.kovospace.newtablinks.visit.models.VisitEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converts {@link VisitEntity} into the shape returned to clients.
 *
 * <p>Mapping is one-directional; see {@link com.kovospace.newtablinks.user.mappers.UserMapper}
 * for why.</p>
 *
 * @since 0.0.19
 */
@Mapper
public interface VisitMapper {

    /**
     * Converts a single visit, flattening its profile to an identifier.
     *
     * @param visitEntity entity to convert
     * @return the converted visit
     */
    @Mapping(target = "profileId", source = "profile.id")
    VisitDto toDto(VisitEntity visitEntity);

    /**
     * Converts a list of visits, preserving order.
     *
     * @param visitEntities entities to convert
     * @return the converted visits, empty when the input is empty
     */
    List<VisitDto> toDtoList(List<VisitEntity> visitEntities);
}
