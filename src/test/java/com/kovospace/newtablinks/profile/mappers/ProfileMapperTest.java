package com.kovospace.newtablinks.profile.mappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.profile.dtos.ProfileDto;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the shape a profile takes on its way out, which is also its shape in the sync snapshot.
 *
 * <p>{@link com.kovospace.newtablinks.sync.services.SyncSnapshotService} builds the snapshot's
 * profiles with this mapper and nothing else, so a field missing here is a field a pulling client
 * never receives - and, because a pull replaces a profile wholesale, one it then loses locally.
 * That is what makes the profile's settings a mapping concern and not only a storage one.</p>
 *
 * @since 0.0.9
 */
class ProfileMapperTest {

    private final ProfileMapper profileMapper = new ProfileMapperImpl();

    @Test
    @DisplayName("puts the drag and drop setting into the shape a client pulls")
    void carriesTheDragAndDropSettingIntoTheDto() {

        final ProfileDto profileDto = profileMapper.toDto(profileWithDragAndDrop(true));

        assertThat(profileDto.enableDragAndDrop()).isTrue();
    }

    @Test
    @DisplayName("reports the drag and drop setting as off rather than leaving it out")
    void carriesTheDragAndDropSettingEvenWhenItIsOff() {

        final ProfileDto profileDto = profileMapper.toDto(profileWithDragAndDrop(false));

        assertThat(profileDto.enableDragAndDrop()).isFalse();
    }

    @Test
    @DisplayName("keeps the name and the position of a profile whose setting is on")
    void doesNotDisturbTheRestOfTheProfile() {

        final ProfileEntity profile = profileWithDragAndDrop(true);
        profile.setName("Work");
        profile.setPosition(2);

        final ProfileDto profileDto = profileMapper.toDto(profile);

        assertThat(profileDto.name()).isEqualTo("Work");
        assertThat(profileDto.position()).isEqualTo(2);
        assertThat(profileDto.enableDragAndDrop()).isTrue();
    }

    @Test
    @DisplayName("puts the dismissal of the tips into the shape a client pulls")
    void carriesTheHideTipsSettingIntoTheDto() {

        final ProfileEntity profile = profileWithDragAndDrop(false);
        profile.setHideTips(true);

        final ProfileDto profileDto = profileMapper.toDto(profile);

        assertThat(profileDto.hideTips()).isTrue();
    }

    @Test
    @DisplayName("reports the tips as shown rather than leaving the setting out")
    void carriesTheHideTipsSettingEvenWhenItIsOff() {

        final ProfileDto profileDto = profileMapper.toDto(profileWithDragAndDrop(false));

        assertThat(profileDto.hideTips()).isFalse();
    }

    @Test
    @DisplayName("keeps the two settings of a profile apart on the way out")
    void doesNotConfuseTheTwoSettings() {

        final ProfileEntity profile = profileWithDragAndDrop(true);
        profile.setHideTips(false);

        final ProfileDto profileDto = profileMapper.toDto(profile);

        assertThat(profileDto.enableDragAndDrop()).isTrue();
        assertThat(profileDto.hideTips()).isFalse();
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Builds a stored profile with its drag and drop setting in a known state.
     *
     * @param enableDragAndDrop the setting to put on it
     * @return the entity
     */
    private ProfileEntity profileWithDragAndDrop(final boolean enableDragAndDrop) {
        final UserEntity owner = mock(UserEntity.class);
        when(owner.getId()).thenReturn(UUID.randomUUID());

        final ProfileEntity profile = new ProfileEntity(owner, "Default", 0);
        profile.setId(UUID.randomUUID());
        profile.setEnableDragAndDrop(enableDragAndDrop);
        return profile;
    }
}
