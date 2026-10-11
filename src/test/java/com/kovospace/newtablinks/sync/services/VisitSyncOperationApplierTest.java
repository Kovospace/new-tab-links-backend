package com.kovospace.newtablinks.sync.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.closedtab.services.ClosedTabSynchronizationService;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.services.ProfileSynchronizationService;
import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.dtos.SyncOperationKind;
import com.kovospace.newtablinks.sync.dtos.SyncPushRequestDto;
import com.kovospace.newtablinks.sync.dtos.SyncPushResultDto;
import com.kovospace.newtablinks.sync.dtos.SyncRejectionReason;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import com.kovospace.newtablinks.visit.dtos.VisitSynchronizedValuesDto;
import com.kovospace.newtablinks.visit.models.VisitEntity;
import com.kovospace.newtablinks.visit.services.VisitSynchronizationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests what a pushed visit operation must carry, and what it does with what it carries.
 *
 * <p>Run through a real {@link SyncPushService}, for the reason given on
 * {@link ProfileSyncOperationApplierTest}. The value under scrutiny is {@code visitedAt}: the
 * client's clock, passed on untouched, and refused rather than stamped when missing - including
 * when the operation carries a closed tab's {@code closedAt} instead, which must not stand in
 * for it.</p>
 *
 * @since 0.0.19
 */
class VisitSyncOperationApplierTest {

    /** The account every operation in this class belongs to. */
    private static final UUID OWNER_ID = UUID.randomUUID();

    /** The profile every visit in this class is in. */
    private static final UUID PROFILE_ID = UUID.randomUUID();

    /** Installation the batch claims to come from. */
    private static final String ORIGIN_DEVICE_ID = "a-device";

    /** A visiting moment far enough in the past to be visibly not "now". */
    private static final Instant VISITED_YESTERDAY = Instant.parse("2026-10-10T18:42:11Z");

    private final VisitSynchronizationService visitSynchronizationService =
            mock(VisitSynchronizationService.class);

    private final ProfileSynchronizationService profileSynchronizationService =
            mock(ProfileSynchronizationService.class);

    private final SyncPushService syncPushService = new SyncPushService(
            List.of(new VisitSyncOperationApplier(
                    visitSynchronizationService, profileSynchronizationService)),
            mock(UserDataChangePublisher.class),
            PermissiveSyncPushLimitGuard.create(),
            mock(ClosedTabSynchronizationService.class),
            mock(VisitSynchronizationService.class));

    @Test
    @DisplayName("stores every field of a visit, with the visiting moment the client sent")
    void shouldPassThePushedVisitOnUntouched() {

        final SyncOperationDto visit = upsertVisit(UUID.randomUUID(), VISITED_YESTERDAY, null);
        profileIsTheOwners();
        storedAsRequested();

        final SyncPushResultDto result = syncPushService.applyPushedOperations(push(visit), OWNER_ID);

        assertThat(result.rejected()).isEmpty();
        verify(visitSynchronizationService).upsertVisitFromPushedOperation(
                eq(visit.id()), any(),
                eq(new VisitSynchronizedValuesDto(
                        "https://spring.io", "Spring Boot reference", "https://spring.io/icon.png",
                        VISITED_YESTERDAY, "Laptop")));
    }

    @Test
    @DisplayName("a visit carrying no visiting moment is refused, even when it carries a closedAt")
    void shouldRefuseAVisitWithNoVisitingMoment() {

        final SyncOperationDto momentless =
                upsertVisit(UUID.randomUUID(), null, VISITED_YESTERDAY);
        profileIsTheOwners();

        final SyncPushResultDto result =
                syncPushService.applyPushedOperations(push(momentless), OWNER_ID);

        assertThat(result.rejected()).singleElement().satisfies(rejection -> {
            assertThat(rejection.entityKind()).isEqualTo(SyncEntityKind.VISIT);
            assertThat(rejection.reason()).isEqualTo(SyncRejectionReason.MISSING_REQUIRED_VALUE);
        });
        verify(visitSynchronizationService, never())
                .upsertVisitFromPushedOperation(any(), any(), any());
    }

    @Test
    @DisplayName("a visit naming a profile this account does not have is refused")
    void shouldRefuseAVisitWhoseProfileIsNotTheOwners() {

        when(profileSynchronizationService.findProfileEntityForOwner(PROFILE_ID, OWNER_ID))
                .thenReturn(Optional.empty());

        final SyncPushResultDto result = syncPushService.applyPushedOperations(
                push(upsertVisit(UUID.randomUUID(), VISITED_YESTERDAY, null)), OWNER_ID);

        assertThat(result.rejected()).singleElement().satisfies(rejection ->
                assertThat(rejection.reason()).isEqualTo(SyncRejectionReason.PARENT_NOT_FOUND));
    }

    @Test
    @DisplayName("a delete removes the visit it names, and one already gone is not a refusal")
    void shouldDeleteTheVisitItNamesWithoutRefusingOneAlreadyGone() {

        final UUID alreadyGone = UUID.randomUUID();
        when(visitSynchronizationService.deleteVisitFromPushedOperationIfPresent(alreadyGone,
                OWNER_ID)).thenReturn(false);

        final SyncPushResultDto result =
                syncPushService.applyPushedOperations(push(deleteVisit(alreadyGone)), OWNER_ID);

        assertThat(result.rejected()).isEmpty();
        verify(visitSynchronizationService)
                .deleteVisitFromPushedOperationIfPresent(alreadyGone, OWNER_ID);
        verify(profileSynchronizationService, never()).findProfileEntityForOwner(any(), any());
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Makes the profile the operations name resolve as one this account owns.
     */
    private void profileIsTheOwners() {
        when(profileSynchronizationService.findProfileEntityForOwner(PROFILE_ID, OWNER_ID))
                .thenReturn(Optional.of(mock(ProfileEntity.class)));
    }

    /**
     * Makes the synchronization service answer with a row stored under the requested identifier.
     */
    private void storedAsRequested() {
        when(visitSynchronizationService.upsertVisitFromPushedOperation(any(), any(), any()))
                .thenAnswer(invocation -> {
                    final VisitEntity storedVisit = mock(VisitEntity.class);
                    when(storedVisit.getId()).thenReturn(invocation.getArgument(0));
                    return storedVisit;
                });
    }

    /**
     * Wraps operations into a batch from one device.
     *
     * @param operations the operations, in the order they were made
     * @return the request to push
     */
    private SyncPushRequestDto push(final SyncOperationDto... operations) {
        return new SyncPushRequestDto(ORIGIN_DEVICE_ID, List.of(operations));
    }

    /**
     * Builds a pushed visit upsert - the shape the extension sends when a page is visited.
     *
     * @param id        identifier of the visit
     * @param visitedAt visiting moment to send, {@code null} to exercise a refusal
     * @param closedAt  a closed tab's moment to send alongside, normally {@code null}
     * @return the operation
     */
    private SyncOperationDto upsertVisit(
            final UUID id, final Instant visitedAt, final Instant closedAt) {

        return new SyncOperationDto(
                SyncOperationKind.UPSERT, SyncEntityKind.VISIT, id,
                PROFILE_ID, null, null, null,
                null, null, "Spring Boot reference", "https://spring.io",
                "https://spring.io/icon.png",
                null, null, null, null, null, null, null, null, null,
                closedAt, visitedAt, "Laptop", null);
    }

    /**
     * Builds a pushed visit delete.
     *
     * @param id identifier of the visit to remove
     * @return the operation
     */
    private SyncOperationDto deleteVisit(final UUID id) {
        return new SyncOperationDto(
                SyncOperationKind.DELETE, SyncEntityKind.VISIT, id,
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
