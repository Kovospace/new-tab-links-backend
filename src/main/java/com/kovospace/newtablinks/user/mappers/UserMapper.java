package com.kovospace.newtablinks.user.mappers;

import com.kovospace.newtablinks.user.dtos.UserDto;
import com.kovospace.newtablinks.user.models.UserEntity;
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
     * <p>Whether the account is pro is not a property of the user row - it is judged from the
     * entitlement at a given moment - so the caller decides it and passes it in.</p>
     *
     * @param userEntity entity to convert
     * @param premium    whether the account is pro right now
     * @return the converted user
     */
    @Mapping(target = "hasPassword", expression = "java(userEntity.hasPassword())")
    @Mapping(target = "premium", source = "premium")
    UserDto toDto(UserEntity userEntity, boolean premium);
}
