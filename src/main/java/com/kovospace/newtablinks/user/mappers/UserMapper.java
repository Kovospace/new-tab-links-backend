package com.kovospace.newtablinks.user.mappers;

import com.kovospace.newtablinks.user.dtos.UserDto;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converts {@link UserEntity} into the shape returned to clients.
 *
 * <p>Mapping is deliberately one-directional. Turning a request body into an entity needs
 * decisions a mapper should not make - resolving parents, assigning positions - so services
 * build entities themselves and use this mapper only on the way out.</p>
 *
 * @since 0.0.1
 */
@Mapper
public interface UserMapper {

    /**
     * Converts a single user.
     *
     * @param userEntity entity to convert
     * @return the converted user
     */
    @Mapping(target = "hasPassword", expression = "java(userEntity.hasPassword())")
    UserDto toDto(UserEntity userEntity);

    /**
     * Converts a list of users, preserving order.
     *
     * @param userEntities entities to convert
     * @return the converted users, empty when the input is empty
     */
    List<UserDto> toDtoList(List<UserEntity> userEntities);
}
