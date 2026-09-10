package com.kovospace.newtablinks.sync.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.group.services.GroupSynchronizationService;
import com.kovospace.newtablinks.subgroup.dtos.SubgroupSynchronizedValuesDto;
import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
import com.kovospace.newtablinks.subgroup.services.SubgroupSynchronizationService;
import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.dtos.SyncOperationKind;
import com.kovospace.newtablinks.sync.models.SyncOperationContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests what a pushed subgroup operation is turned into before it is stored.
 *
 * <p>The flags are the interesting part: they are boxed on the wire so that an operation about
 * another kind of record can leave them out, and unboxing them is where a pushed {@code true}
 * would be lost.</p>
 *
 * @since 0.0.8
 */
class SubgroupSyncOperationApplierTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID PARENT_GROUP_ID = UUID.randomUUID();

    private final SubgroupSynchronizationService subgroupSynchronizationService =
            mock(SubgroupSynchronizationService.class);
    private final GroupSynchronizationService groupSynchronizationService =
            mock(GroupSynchronizationService.class);

    private final SubgroupSyncOperationApplier subgroupSyncOperationApplier =
            new SubgroupSyncOperationApplier(
                    subgroupSynchronizationService, groupSynchronizationService);

    private final SyncOperationContext context = new SyncOperationContext(OWNER_ID);

    @Test
    @DisplayName("carries a pushed tab group setting through to the subgroup being stored")
    void passesThePushedTabGroupSettingToTheSynchronizationService() {

        assertThat(applyUpsertAndCaptureStoredValues(Boolean.TRUE).catchLinksIntoTabGroup())
                .isTrue();
    }

    @Test
    @DisplayName("treats an operation that omits the tab group setting as having it switched off")
    void defaultsAnOmittedTabGroupSettingToFalse() {

        // An older extension build sends no such field at all, and a subgroup it pushes must not
        // acquire a behaviour its user never asked for.
        assertThat(applyUpsertAndCaptureStoredValues(null).catchLinksIntoTabGroup()).isFalse();
    }

    @Test
    @DisplayName("keeps the tab group setting independent of the two folded states")
    void doesNotConfuseTheTabGroupSettingWithTheCollapseFlags() {

        final SubgroupSynchronizedValuesDto storedValues =
                applyUpsertAndCaptureStoredValues(Boolean.TRUE);

        assertThat(storedValues.collapseState().collapsed()).isFalse();
        assertThat(storedValues.collapseState().defaultCollapsed()).isFalse();
        assertThat(storedValues.catchLinksIntoTabGroup()).isTrue();
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Applies one upsert and returns the values the applier asked to have stored.
     *
     * @param catchLinksIntoTabGroup the flag as the operation carries it, {@code null} when the
     *                               operation leaves it out
     * @return the captured values
     */
    private SubgroupSynchronizedValuesDto applyUpsertAndCaptureStoredValues(
            final Boolean catchLinksIntoTabGroup) {

        final GroupEntity parentGroup = mock(GroupEntity.class);
        when(groupSynchronizationService.findGroupEntityForOwner(PARENT_GROUP_ID, OWNER_ID))
                .thenReturn(Optional.of(parentGroup));

        final SubgroupEntity storedSubgroup = mock(SubgroupEntity.class);
        when(storedSubgroup.getId()).thenReturn(UUID.randomUUID());
        when(subgroupSynchronizationService.upsertSubgroupFromPushedOperation(
                any(), eq(parentGroup), any())).thenReturn(storedSubgroup);

        subgroupSyncOperationApplier.applyUpsert(
                upsertSubgroup(catchLinksIntoTabGroup), context);

        final ArgumentCaptor<SubgroupSynchronizedValuesDto> storedValues =
                ArgumentCaptor.forClass(SubgroupSynchronizedValuesDto.class);
        verify(subgroupSynchronizationService).upsertSubgroupFromPushedOperation(
                any(), eq(parentGroup), storedValues.capture());

        return storedValues.getValue();
    }

    /**
     * Builds an otherwise ordinary subgroup upsert.
     *
     * @param catchLinksIntoTabGroup the flag to put on it, may be {@code null}
     * @return the operation
     */
    private SyncOperationDto upsertSubgroup(final Boolean catchLinksIntoTabGroup) {
        return new SyncOperationDto(
                SyncOperationKind.UPSERT, SyncEntityKind.SUBGROUP, UUID.randomUUID(),
                null, null, PARENT_GROUP_ID, null,
                "Internal", null, null, null, null,
                null, null, catchLinksIntoTabGroup, null, 0);
    }
}
