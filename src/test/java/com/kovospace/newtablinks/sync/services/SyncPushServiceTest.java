package com.kovospace.newtablinks.sync.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.closedtab.services.ClosedTabSynchronizationService;
import com.kovospace.newtablinks.common.models.EffectivePlanLimits;
import com.kovospace.newtablinks.common.models.PlanLimitValues;
import com.kovospace.newtablinks.common.models.PushFootprint;
import com.kovospace.newtablinks.common.models.PushLimitBaseline;
import com.kovospace.newtablinks.common.services.SyncPushLimitGuard;
import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.dtos.SyncOperationKind;
import com.kovospace.newtablinks.sync.dtos.SyncPushRequestDto;
import com.kovospace.newtablinks.sync.dtos.SyncPushResultDto;
import com.kovospace.newtablinks.sync.dtos.SyncRejectionReason;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import com.kovospace.newtablinks.sync.exceptions.SyncOperationRejectedException;
import com.kovospace.newtablinks.sync.models.SyncOperationContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Tests how a pushed batch is dispatched, and what a single bad operation does to the rest.
 *
 * @since 0.0.5
 */
class SyncPushServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final String ORIGIN_DEVICE_ID = "a-device";

    private final SyncOperationApplier linkApplier = mock(SyncOperationApplier.class);
    private final UserDataChangePublisher userDataChangePublisher =
            mock(UserDataChangePublisher.class);

    private final SyncPushLimitGuard syncPushLimitGuard = PermissiveSyncPushLimitGuard.create();
    private final ClosedTabSynchronizationService closedTabSynchronizationService =
            mock(ClosedTabSynchronizationService.class);

    private SyncPushService syncPushService;

    @BeforeEach
    void createServiceWithOneApplier() {
        // Stubbed before construction: the service indexes its appliers by the kind they claim.
        when(linkApplier.supportedEntityKind()).thenReturn(SyncEntityKind.LINK);
        syncPushService = new SyncPushService(List.of(linkApplier), userDataChangePublisher,
                syncPushLimitGuard, closedTabSynchronizationService);
    }

    @Test
    @DisplayName("hands an upsert to the applier that owns that kind of record")
    void dispatchesAnUpsertToItsApplier() {

        final SyncOperationDto upsert = upsertLink(UUID.randomUUID());
        when(linkApplier.applyUpsert(any(), any())).thenReturn(upsert.id());

        syncPushService.applyPushedOperations(push(upsert), OWNER_ID);

        verify(linkApplier).applyUpsert(any(SyncOperationDto.class), any(SyncOperationContext.class));
        verify(linkApplier, never()).applyDelete(any(), any());
    }

    @Test
    @DisplayName("hands a delete to the applier that owns that kind of record")
    void dispatchesADeleteToItsApplier() {

        syncPushService.applyPushedOperations(push(deleteLink(UUID.randomUUID())), OWNER_ID);

        verify(linkApplier).applyDelete(any(SyncOperationDto.class), any(SyncOperationContext.class));
        verify(linkApplier, never()).applyUpsert(any(), any());
    }

    @Test
    @DisplayName("reports an identifier the server could not keep")
    void reportsARemappedIdentifier() {

        final SyncOperationDto upsert = upsertLink(UUID.randomUUID());
        final UUID storedInstead = UUID.randomUUID();
        when(linkApplier.applyUpsert(any(), any())).thenReturn(storedInstead);

        final SyncPushResultDto result = syncPushService.applyPushedOperations(push(upsert), OWNER_ID);

        assertThat(result.remaps()).singleElement().satisfies(remap -> {
            assertThat(remap.clientId()).isEqualTo(upsert.id());
            assertThat(remap.serverId()).isEqualTo(storedInstead);
        });
    }

    @Test
    @DisplayName("refuses one operation without abandoning the rest of the batch")
    void refusesOneOperationAndCarriesOn() {

        final SyncOperationDto doomed = upsertLink(UUID.randomUUID());
        final SyncOperationDto fine = upsertLink(UUID.randomUUID());

        when(linkApplier.applyUpsert(any(), any()))
                .thenThrow(new SyncOperationRejectedException(SyncRejectionReason.PARENT_NOT_FOUND))
                .thenReturn(fine.id());

        final SyncPushResultDto result =
                syncPushService.applyPushedOperations(push(doomed, fine), OWNER_ID);

        assertThat(result.rejected()).singleElement().satisfies(rejection -> {
            assertThat(rejection.index()).isZero();
            assertThat(rejection.id()).isEqualTo(doomed.id());
            assertThat(rejection.reason()).isEqualTo(SyncRejectionReason.PARENT_NOT_FOUND);
        });
        // The second operation still ran - a batch is not abandoned because one entry was bad.
        verify(linkApplier, times(2)).applyUpsert(any(), any());
    }

    @Test
    @DisplayName("announces the change once, naming the device that pushed it")
    void announcesTheChangeOnceForTheWholeBatch() {

        when(linkApplier.applyUpsert(any(), any())).thenReturn(UUID.randomUUID());

        syncPushService.applyPushedOperations(
                push(upsertLink(UUID.randomUUID()), upsertLink(UUID.randomUUID())), OWNER_ID);

        verify(userDataChangePublisher, times(1)).publishChangeFor(OWNER_ID, ORIGIN_DEVICE_ID);
    }

    @Test
    @DisplayName("announces nothing when the batch is empty")
    void announcesNothingForAnEmptyBatch() {

        syncPushService.applyPushedOperations(
                new SyncPushRequestDto(ORIGIN_DEVICE_ID, List.of()), OWNER_ID);

        verify(userDataChangePublisher, never()).publishChangeFor(any(), any());
    }

    @Test
    @DisplayName("takes the baseline before the first operation, trims closed tabs to the plan "
            + "and judges the end state with what the operations wrote into, after the last")
    void judgesThePlanOnTheBatchAsAWhole() {

        final PushLimitBaseline baseline = new PushLimitBaseline(
                new EffectivePlanLimits(false, new PlanLimitValues(1, 2, 25, 25, 500, 25, 5)),
                Map.of(), Map.of(), Map.of(), 40);
        when(syncPushLimitGuard.captureBaselineBeforeBatch(OWNER_ID)).thenReturn(baseline);
        when(linkApplier.applyUpsert(any(), any())).thenReturn(UUID.randomUUID());

        syncPushService.applyPushedOperations(
                push(upsertLink(UUID.randomUUID()), deleteLink(UUID.randomUUID())), OWNER_ID);

        final InOrder order = inOrder(syncPushLimitGuard, linkApplier, closedTabSynchronizationService);
        order.verify(syncPushLimitGuard).captureBaselineBeforeBatch(OWNER_ID);
        order.verify(linkApplier).applyUpsert(any(), any());
        order.verify(linkApplier).applyDelete(any(), any());
        order.verify(closedTabSynchronizationService).trimHistoryToPlanLimit(OWNER_ID, 25, 40);
        order.verify(syncPushLimitGuard).requireEndStateWithinLimits(
                eq(OWNER_ID), eq(baseline), any(PushFootprint.class));
    }

    @Test
    @DisplayName("an empty batch neither locks the account nor counts anything")
    void leavesTheCapsAloneForAnEmptyBatch() {

        syncPushService.applyPushedOperations(
                new SyncPushRequestDto(ORIGIN_DEVICE_ID, List.of()), OWNER_ID);

        verifyNoInteractions(syncPushLimitGuard, closedTabSynchronizationService);
    }

    // ------------------------------------------------------------------ fixtures

    private SyncPushRequestDto push(final SyncOperationDto... operations) {
        return new SyncPushRequestDto(ORIGIN_DEVICE_ID, List.of(operations));
    }

    private SyncOperationDto upsertLink(final UUID id) {
        return operation(SyncOperationKind.UPSERT, id);
    }

    private SyncOperationDto deleteLink(final UUID id) {
        return operation(SyncOperationKind.DELETE, id);
    }

    private SyncOperationDto operation(final SyncOperationKind kind, final UUID id) {
        return new SyncOperationDto(
                kind, SyncEntityKind.LINK, id,
                null, null, UUID.randomUUID(), null,
                null, null, "A link", "https://example.test", null,
                null, null, null, null, null, null, null,
                null, null, 0);
    }
}
