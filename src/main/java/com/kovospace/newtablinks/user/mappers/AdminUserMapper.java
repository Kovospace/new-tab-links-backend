package com.kovospace.newtablinks.user.mappers;

import com.kovospace.newtablinks.entitlement.models.ProStanding;
import com.kovospace.newtablinks.user.dtos.AdminUserDto;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converts {@link UserEntity} into the fuller shape the operator sees.
 *
 * <p>Separate from {@link UserMapper} rather than an extra method on it, because the two answer
 * different questions and must not drift into each other: a field added for the operator has to
 * be added here deliberately, not appear in a user's own profile because a mapper was reused.</p>
 *
 * <p>The account's pro standing is not on the entity; the caller reads it from the entitlement
 * module and hands it in, for a list in one batch rather than one query per account.</p>
 *
 * @since 0.0.6
 */
@Mapper
public interface AdminUserMapper {

    /**
     * Converts a single account.
     *
     * @param userEntity  entity to convert
     * @param proStanding whether the account is pro right now, and through what
     * @return the converted account
     */
    @Mapping(target = "hasPassword", expression = "java(userEntity.hasPassword())")
    @Mapping(target = "premium", source = "proStanding.premium")
    @Mapping(target = "premiumSource", source = "proStanding.premiumSource")
    AdminUserDto toDto(UserEntity userEntity, ProStanding proStanding);

    /**
     * Converts a list of accounts, preserving order.
     *
     * @param userEntities       entities to convert
     * @param proStandingByOwner the pro standing of each account, by its identifier; an account
     *                           missing from it is rendered as not pro
     * @return the converted accounts, empty when the input is empty
     * @since 0.0.10
     */
    default List<AdminUserDto> toDtoList(
            final List<UserEntity> userEntities,
            final Map<UUID, ProStanding> proStandingByOwner) {

        return userEntities.stream()
                .map(userEntity -> toDto(userEntity, proStandingByOwner.getOrDefault(
                        userEntity.getId(), ProStanding.NOT_PRO)))
                .toList();
    }
}
