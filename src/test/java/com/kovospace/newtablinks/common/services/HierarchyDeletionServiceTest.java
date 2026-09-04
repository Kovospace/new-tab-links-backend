package com.kovospace.newtablinks.common.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import com.kovospace.newtablinks.environment.repositories.EnvironmentRepository;
import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.group.repositories.GroupRepository;
import com.kovospace.newtablinks.link.models.LinkEntity;
import com.kovospace.newtablinks.link.repositories.LinkRepository;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.repositories.ProfileRepository;
import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
import com.kovospace.newtablinks.subgroup.repositories.SubgroupRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Tests that a record is never removed while one of its children still names it.
 *
 * <p>No entity in the hierarchy declares the association from the parent's side, so nothing
 * cascades and the order these statements are issued in is the only thing keeping the foreign
 * keys satisfied. That order is what is asserted here.</p>
 *
 * @since 0.0.7
 */
class HierarchyDeletionServiceTest {

    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
    private final GroupRepository groupRepository = mock(GroupRepository.class);
    private final SubgroupRepository subgroupRepository = mock(SubgroupRepository.class);
    private final LinkRepository linkRepository = mock(LinkRepository.class);

    private final HierarchyDeletionService hierarchyDeletionService = new HierarchyDeletionService(
            profileRepository, environmentRepository, groupRepository, subgroupRepository,
            linkRepository);

    @Test
    @DisplayName("empties a profile from the links upwards before removing the profile itself")
    void deletesAWholeProfileDeepestFirst() {

        final ProfileEntity profile = entity(mock(ProfileEntity.class));
        final EnvironmentEntity environment = entity(mock(EnvironmentEntity.class));
        final GroupEntity group = entity(mock(GroupEntity.class));
        final SubgroupEntity subgroup = entity(mock(SubgroupEntity.class));
        final LinkEntity link = mock(LinkEntity.class);

        when(environmentRepository.findAllByProfileId(profile.getId()))
                .thenReturn(List.of(environment));
        when(groupRepository.findAllByEnvironmentIdOrderByPositionAsc(environment.getId()))
                .thenReturn(List.of(group));
        when(linkRepository.findAllByParentGroupId(group.getId()))
                .thenReturn(List.of(link));
        when(subgroupRepository.findAllByParentGroupIdOrderByPositionAsc(group.getId()))
                .thenReturn(List.of(subgroup));

        hierarchyDeletionService.deleteProfileWithDescendants(profile);

        final InOrder order = inOrder(
                linkRepository, subgroupRepository, groupRepository, environmentRepository,
                profileRepository);

        order.verify(linkRepository).deleteAll(List.of(link));
        order.verify(subgroupRepository).deleteAll(List.of(subgroup));
        order.verify(groupRepository).delete(group);
        order.verify(environmentRepository).delete(environment);
        order.verify(profileRepository).delete(profile);
    }

    @Test
    @DisplayName("clears a group's nested links too, not only the ones sitting directly in it")
    void clearsEveryLinkOfAGroupWhicheverSubgroupItIsIn() {

        final GroupEntity group = entity(mock(GroupEntity.class));
        final LinkEntity directLink = mock(LinkEntity.class);
        final LinkEntity nestedLink = mock(LinkEntity.class);

        when(linkRepository.findAllByParentGroupId(group.getId()))
                .thenReturn(List.of(directLink, nestedLink));

        hierarchyDeletionService.deleteGroupWithDescendants(group);

        verify(linkRepository).deleteAll(List.of(directLink, nestedLink));
        verify(groupRepository).delete(group);
    }

    @Test
    @DisplayName("removes a subgroup's links before the subgroup")
    void deletesASubgroupDeepestFirst() {

        final SubgroupEntity subgroup = entity(mock(SubgroupEntity.class));
        final LinkEntity link = mock(LinkEntity.class);

        when(linkRepository.findAllByParentSubgroupIdOrderByPositionAsc(subgroup.getId()))
                .thenReturn(List.of(link));

        hierarchyDeletionService.deleteSubgroupWithDescendants(subgroup);

        final InOrder order = inOrder(linkRepository, subgroupRepository);
        order.verify(linkRepository).deleteAll(List.of(link));
        order.verify(subgroupRepository).delete(subgroup);
    }

    @Test
    @DisplayName("deletes an empty profile without touching anything beneath it")
    void deletesAnEmptyProfileOnItsOwn() {

        final ProfileEntity profile = entity(mock(ProfileEntity.class));
        when(environmentRepository.findAllByProfileId(profile.getId())).thenReturn(List.of());

        hierarchyDeletionService.deleteProfileWithDescendants(profile);

        verify(profileRepository).delete(profile);
        verify(environmentRepository, never()).delete(any(EnvironmentEntity.class));
        verify(linkRepository, never()).deleteAll(anyList());
    }

    /** Gives a mocked entity an identifier, which is all this service reads off one. */
    private static <ENTITY extends AbstractAuditableEntity> ENTITY entity(final ENTITY mocked) {
        when(mocked.getId()).thenReturn(UUID.randomUUID());
        return mocked;
    }
}
