package com.kovospace.newtablinks.user.mappers;

import com.kovospace.newtablinks.user.dtos.AdminUserDto;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converts {@link UserEntity} into the fuller shape the operator sees.
 *
 * <p>Separate from {@link UserMapper} rather than an extra method on it, because the two answer
 * different questions and must not drift into each other: a field added for the operator has to
 * be added here deliberately, not appear in a user's own profile because a mapper was reused.</p>
 *
 * @since 0.0.6
 */
@Mapper
public interface AdminUserMapper {

    /**
     * Converts a single account.
     *
     * @param userEntity entity to convert
     * @return the converted account
     */
    @Mapping(target = "hasPassword", expression = "java(userEntity.hasPassword())")
    AdminUserDto toDto(UserEntity userEntity);

    /**
     * Converts a list of accounts, preserving order.
     *
     * @param userEntities entities to convert
     * @return the converted accounts, empty when the input is empty
     */
    List<AdminUserDto> toDtoList(List<UserEntity> userEntities);
}
