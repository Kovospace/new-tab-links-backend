package com.kovospace.newtablinks.subgroup.mappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.subgroup.dtos.SubgroupDto;
import com.kovospace.newtablinks.subgroup.models.SubgroupCollapseState;
import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the shape a subgroup takes on its way out, which is also its shape in the sync snapshot.
 *
 * <p>{@link com.kovospace.newtablinks.sync.services.SyncSnapshotService} builds the snapshot's
 * subgroups with this mapper and nothing else, so a field missing here is a field a pulling client
 * never receives - and, because a pull replaces a profile wholesale, one it then loses locally.</p>
 *
 * @since 0.0.8
 */
class SubgroupMapperTest {

    private final SubgroupMapper subgroupMapper = new SubgroupMapperImpl();

    @Test
    @DisplayName("puts the tab group setting into the shape a client pulls")
    void carriesTheTabGroupSettingIntoTheDto() {

        final SubgroupDto subgroupDto = subgroupMapper.toDto(subgroupCatchingLinks(true));

        assertThat(subgroupDto.catchLinksIntoTabGroup()).isTrue();
    }

    @Test
    @DisplayName("reports the tab group setting as off rather than leaving it out")
    void carriesTheTabGroupSettingEvenWhenItIsOff() {

        final SubgroupDto subgroupDto = subgroupMapper.toDto(subgroupCatchingLinks(false));

        assertThat(subgroupDto.catchLinksIntoTabGroup()).isFalse();
    }

    @Test
    @DisplayName("keeps the tab group setting apart from the two folded states")
    void doesNotConfuseTheTabGroupSettingWithTheCollapseFlags() {

        final SubgroupEntity subgroup = subgroupCatchingLinks(true);
        subgroup.setCollapsed(false);
        subgroup.setDefaultCollapsed(false);

        final SubgroupDto subgroupDto = subgroupMapper.toDto(subgroup);

        assertThat(subgroupDto.collapsed()).isFalse();
        assertThat(subgroupDto.defaultCollapsed()).isFalse();
        assertThat(subgroupDto.catchLinksIntoTabGroup()).isTrue();
    }

    @Test
    @DisplayName("puts the colour into the shape a client pulls")
    void carriesTheColorIntoTheDto() {

        final SubgroupEntity subgroup = subgroupCatchingLinks(false);
        subgroup.setColor("cyan");

        assertThat(subgroupMapper.toDto(subgroup).color()).isEqualTo("cyan");
    }

    @Test
    @DisplayName("reports a subgroup that has never been coloured as having no colour")
    void reportsAnAbsentColorAsNull() {

        // Absent rather than defaulted: this is what every subgroup stored before the column
        // existed looks like, and the extension is what decides to give it a colour.
        assertThat(subgroupMapper.toDto(subgroupCatchingLinks(false)).color()).isNull();
    }

    @Test
    @DisplayName("keeps the colour apart from the subgroup's own free text")
    void doesNotConfuseTheColorWithTheNameOrDescription() {

        final SubgroupEntity subgroup = subgroupCatchingLinks(false);
        subgroup.setDescription("Only reachable on the VPN");
        subgroup.setColor("cyan");

        final SubgroupDto subgroupDto = subgroupMapper.toDto(subgroup);

        assertThat(subgroupDto.name()).isEqualTo("Internal");
        assertThat(subgroupDto.description()).isEqualTo("Only reachable on the VPN");
        assertThat(subgroupDto.color()).isEqualTo("cyan");
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Builds a stored subgroup with its tab group setting in a known state.
     *
     * @param catchLinksIntoTabGroup the setting to put on it
     * @return the entity
     */
    private SubgroupEntity subgroupCatchingLinks(final boolean catchLinksIntoTabGroup) {
        final GroupEntity parentGroup = mock(GroupEntity.class);
        when(parentGroup.getId()).thenReturn(UUID.randomUUID());

        final SubgroupEntity subgroup = new SubgroupEntity(
                parentGroup, "Internal", 0, new SubgroupCollapseState(false, false));
        subgroup.setId(UUID.randomUUID());
        subgroup.setCatchLinksIntoTabGroup(catchLinksIntoTabGroup);
        return subgroup;
    }
}
