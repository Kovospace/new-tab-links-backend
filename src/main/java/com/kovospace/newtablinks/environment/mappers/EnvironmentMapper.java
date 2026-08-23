package com.kovospace.newtablinks.environment.mappers;

import com.kovospace.newtablinks.environment.dtos.EnvironmentDto;
import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converts {@link EnvironmentEntity} into the shape returned to clients.
 *
 * <p>Mapping is one-directional; see {@link com.kovospace.newtablinks.user.mappers.UserMapper}
 * for why.</p>
 *
 * @since 0.0.1
 */
@Mapper
public interface EnvironmentMapper {

    /**
     * Converts a single environment, flattening the owner to its identifier.
     *
     * @param environmentEntity entity to convert
     * @return the converted environment
     */
    @Mapping(target = "ownerId", source = "owner.id")
    EnvironmentDto toDto(EnvironmentEntity environmentEntity);

    /**
     * Converts a list of environments, preserving order.
     *
     * @param environmentEntities entities to convert
     * @return the converted environments, empty when the input is empty
     */
    List<EnvironmentDto> toDtoList(List<EnvironmentEntity> environmentEntities);
}
