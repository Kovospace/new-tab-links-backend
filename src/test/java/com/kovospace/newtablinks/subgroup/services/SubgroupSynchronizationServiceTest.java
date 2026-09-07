package com.kovospace.newtablinks.subgroup.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.common.services.HierarchyDeletionService;
import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.subgroup.dtos.SubgroupSynchronizedValuesDto;
import com.kovospace.newtablinks.subgroup.models.SubgroupCollapseState;
import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
import com.kovospace.newtablinks.subgroup.repositories.SubgroupRepository;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests that a pushed subgroup lands on the row with every synchronized field it carried.
 *
 * <p>A pull replaces a profile wholesale, so a field the server accepts but never writes is worse
 * than one it rejects: the device that made the change keeps it until its next pull and then loses
 * it silently.</p>
 *
 * @since 0.0.8
 */
class SubgroupSynchronizationServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID SUBGROUP_ID = UUID.randomUUID();

    private final SubgroupRepository subgroupRepository = mock(SubgroupRepository.class);
    private final UserDataChangePublisher userDataChangePublisher =
            mock(UserDataChangePublisher.class);
    private final HierarchyDeletionService hierarchyDeletionService =
            mock(HierarchyDeletionService.class);

    private final SubgroupSynchronizationService subgroupSynchronizationService =
            new SubgroupSynchronizationService(
                    subgroupRepository, userDataChangePublisher, hierarchyDeletionService);

    private final GroupEntity parentGroup = groupOwnedBy(OWNER_ID);

    @Test
    @DisplayName("stores the tab group setting on a subgroup the account has never seen")
    void writesTheTabGroupSettingWhenInsertingASubgroup() {

        when(subgroupRepository.findByIdAndOwnerId(SUBGROUP_ID, OWNER_ID))
                .thenReturn(Optional.empty());
        when(subgroupRepository.save(any(SubgroupEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        subgroupSynchronizationService.upsertSubgroupFromPushedOperation(
                SUBGROUP_ID, parentGroup, pushedValues(true));

        final ArgumentCaptor<SubgroupEntity> insertedSubgroup =
                ArgumentCaptor.forClass(SubgroupEntity.class);
        verify(subgroupRepository).save(insertedSubgroup.capture());

        assertThat(insertedSubgroup.getValue().isCatchLinksIntoTabGroup()).isTrue();
    }

    @Test
    @DisplayName("switches the tab group setting on for a subgroup the account already holds")
    void writesTheTabGroupSettingWhenUpdatingASubgroup() {

        final SubgroupEntity existingSubgroup = storedSubgroupWithTabGroupSetting(false);
        when(subgroupRepository.findByIdAndOwnerId(SUBGROUP_ID, OWNER_ID))
                .thenReturn(Optional.of(existingSubgroup));

        subgroupSynchronizationService.upsertSubgroupFromPushedOperation(
                SUBGROUP_ID, parentGroup, pushedValues(true));

        assertThat(existingSubgroup.isCatchLinksIntoTabGroup()).isTrue();
    }

    @Test
    @DisplayName("switches the tab group setting back off when the push says it is off")
    void clearsTheTabGroupSettingWhenTheOperationSaysItIsOff() {

        // The upsert replaces every synchronized field; there is no tombstone saying "turned off",
        // so a false that fails to overwrite a stored true is a switch the user cannot undo.
        final SubgroupEntity existingSubgroup = storedSubgroupWithTabGroupSetting(true);
        when(subgroupRepository.findByIdAndOwnerId(SUBGROUP_ID, OWNER_ID))
                .thenReturn(Optional.of(existingSubgroup));

        subgroupSynchronizationService.upsertSubgroupFromPushedOperation(
                SUBGROUP_ID, parentGroup, pushedValues(false));

        assertThat(existingSubgroup.isCatchLinksIntoTabGroup()).isFalse();
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Builds the values a pushed subgroup upsert carries.
     *
     * @param catchLinksIntoTabGroup the tab group setting to push
     * @return the values
     */
    private SubgroupSynchronizedValuesDto pushedValues(final boolean catchLinksIntoTabGroup) {
        return new SubgroupSynchronizedValuesDto(
                "Internal", null, new SubgroupCollapseState(false, false),
                catchLinksIntoTabGroup, 0);
    }

    /**
     * Builds a subgroup as it is already stored, with the tab group setting in a known state.
     *
     * @param catchLinksIntoTabGroup the stored setting
     * @return the entity
     */
    private SubgroupEntity storedSubgroupWithTabGroupSetting(
            final boolean catchLinksIntoTabGroup) {

        final SubgroupEntity storedSubgroup = new SubgroupEntity(
                parentGroup, "Internal", 0, new SubgroupCollapseState(false, false));
        storedSubgroup.setId(SUBGROUP_ID);
        storedSubgroup.setCatchLinksIntoTabGroup(catchLinksIntoTabGroup);
        return storedSubgroup;
    }

    /**
     * Builds a group whose owner the service can read, which is where it takes the owner from.
     *
     * @param ownerId identifier the group's environment reports as its owner
     * @return the mocked group
     */
    private GroupEntity groupOwnedBy(final UUID ownerId) {
        final UserEntity owner = mock(UserEntity.class);
        when(owner.getId()).thenReturn(ownerId);

        final EnvironmentEntity environment = mock(EnvironmentEntity.class);
        when(environment.getOwner()).thenReturn(owner);

        final GroupEntity group = mock(GroupEntity.class);
        when(group.getEnvironment()).thenReturn(environment);
        return group;
    }
}
