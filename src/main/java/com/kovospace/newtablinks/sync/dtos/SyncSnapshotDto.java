package com.kovospace.newtablinks.sync.dtos;

import com.kovospace.newtablinks.closedtab.dtos.ClosedTabDto;
import com.kovospace.newtablinks.environment.dtos.EnvironmentDto;
import com.kovospace.newtablinks.group.dtos.GroupDto;
import com.kovospace.newtablinks.link.dtos.LinkDto;
import com.kovospace.newtablinks.profile.dtos.ProfileDto;
import com.kovospace.newtablinks.subgroup.dtos.SubgroupDto;
import com.kovospace.newtablinks.user.dtos.UserDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Everything one user owns, in a single response.
 *
 * <p>Every collection is ordered for display except {@code closedTabs}, which is ordered by when
 * each tab was closed - the only order that list is ever read in, and the client's own clock
 * rather than this server's.</p>
 *
 * <p>The collections are flat rather than nested, deliberately: the browser extension stores its
 * state as flat maps keyed by identifier, so a flat snapshot maps onto it without the client
 * having to take a tree apart. Each child names its parent by identifier.</p>
 *
 * @param owner        the user this snapshot belongs to
 * @param profiles     the owner's profiles, in display order
 * @param environments the owner's environments, in display order
 * @param groups       every group of those environments, in display order
 * @param subgroups    every subgroup of those groups, in display order
 * @param links        every link of those groups and subgroups, in display order
 * @param closedTabs   every closed tab of those profiles, most recently closed first
 * @param capturedAt   moment the snapshot was assembled
 * @since 0.0.1
 */
@Schema(description = "Everything one user owns, as flat collections keyed by identifier")
public record SyncSnapshotDto(

        @Schema(description = "The user this snapshot belongs to")
        UserDto owner,

        @Schema(description = "The owner's profiles, in display order")
        List<ProfileDto> profiles,

        @Schema(description = "The owner's environments, in display order")
        List<EnvironmentDto> environments,

        @Schema(description = "Every group of those environments, in display order")
        List<GroupDto> groups,

        @Schema(description = "Every subgroup of those groups, in display order")
        List<SubgroupDto> subgroups,

        @Schema(description = "Every link of those groups and subgroups, in display order")
        List<LinkDto> links,

        @Schema(description = "Every closed tab of those profiles, most recently closed first")
        List<ClosedTabDto> closedTabs,

        @Schema(description = "Moment the snapshot was assembled", example = "2026-08-24T10:15:30Z")
        Instant capturedAt) {
}
