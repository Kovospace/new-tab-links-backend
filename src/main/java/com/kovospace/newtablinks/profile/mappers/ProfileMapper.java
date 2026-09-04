package com.kovospace.newtablinks.profile.mappers;

import com.kovospace.newtablinks.profile.dtos.ProfileDto;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converts {@link ProfileEntity} into the shape returned to clients.
 *
 * <p>Mapping is one-directional; see {@link com.kovospace.newtablinks.user.mappers.UserMapper}
 * for why.</p>
 *
 * @since 0.0.6
 */
@Mapper
public interface ProfileMapper {

    /**
     * Converts a single profile, flattening the owner to its identifier.
     *
     * @param profileEntity entity to convert
     * @return the converted profile
     */
    @Mapping(target = "ownerId", source = "owner.id")
    ProfileDto toDto(ProfileEntity profileEntity);

    /**
     * Converts a list of profiles, preserving order.
     *
     * @param profileEntities entities to convert
     * @return the converted profiles, empty when the input is empty
     */
    List<ProfileDto> toDtoList(List<ProfileEntity> profileEntities);
}
