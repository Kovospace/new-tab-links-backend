package com.kovospace.newtablinks.group.mappers;

import com.kovospace.newtablinks.group.dtos.GroupDto;
import com.kovospace.newtablinks.group.models.GroupEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converts {@link GroupEntity} into the shape returned to clients.
 *
 * <p>Mapping is one-directional; see {@link com.kovospace.newtablinks.user.mappers.UserMapper}
 * for why.</p>
 *
 * @since 0.0.1
 */
@Mapper
public interface GroupMapper {

    /**
     * Converts a single group, flattening the environment to its identifier.
     *
     * @param groupEntity entity to convert
     * @return the converted group
     */
    @Mapping(target = "environmentId", source = "environment.id")
    GroupDto toDto(GroupEntity groupEntity);

    /**
     * Converts a list of groups, preserving order.
     *
     * @param groupEntities entities to convert
     * @return the converted groups, empty when the input is empty
     */
    List<GroupDto> toDtoList(List<GroupEntity> groupEntities);
}
