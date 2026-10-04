package com.kovospace.newtablinks.sync.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.closedtab.dtos.ClosedTabSynchronizedValuesDto;
import com.kovospace.newtablinks.closedtab.models.ClosedTabEntity;
import com.kovospace.newtablinks.closedtab.services.ClosedTabSynchronizationService;
import com.kovospace.newtablinks.common.services.FairUseLimitGuard;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.services.ProfileSynchronizationService;
import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.dtos.SyncOperationKind;
import com.kovospace.newtablinks.sync.dtos.SyncPushRequestDto;
import com.kovospace.newtablinks.sync.dtos.SyncPushResultDto;
import com.kovospace.newtablinks.sync.dtos.SyncRejectionReason;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests what a pushed closed tab operation must carry, and what it does with what it carries.
 *
 * <p>Run through a real {@link SyncPushService} rather than against the applier alone, for the
 * reason given on {@link ProfileSyncOperationApplierTest}: what the extension depends on is the
 * combination of applying and refusing.</p>
 *
 * <p>The value under the most scrutiny here is {@code closedAt}. It is the client's clock, the
 * list is ordered by it, and the row often reaches the server long after the tab was closed - so
 * a server timestamp quietly substituted for a missing or an inconvenient one would reorder
 * somebody's history rather than fail visibly. Two tests pin that: one that the pushed value is
 * passed on untouched, and one that an operation without it is refused rather than stamped.</p>
 *
 * @since 0.0.8
 */
class ClosedTabSyncOperationApplierTest {

    /** The account every operation in this class belongs to. */
    private static final UUID OWNER_ID = UUID.randomUUID();

    /** The profile every closed tab in this class hangs off. */
    private static final UUID PROFILE_ID = UUID.randomUUID();

    /** Installation the batch claims to come from, echoed on the refresh notification. */
    private static final String ORIGIN_DEVICE_ID = "a-device";

    /** A closing moment far enough in the past to be visibly not "now". */
    private static final Instant CLOSED_YESTERDAY = Instant.parse("2026-09-10T18:42:11Z");

    private final ClosedTabSynchronizationService closedTabSynchronizationService =
            mock(ClosedTabSynchronizationService.class);

    private final ProfileSynchronizationService profileSynchronizationService =
            mock(ProfileSynchronizationService.class);

    private final SyncPushService syncPushService = new SyncPushService(
            List.of(new ClosedTabSyncOperationApplier(
                    closedTabSynchronizationService, profileSynchronizationService)),
            mock(UserDataChangePublisher.class),
            mock(FairUseLimitGuard.class),
            mock(ClosedTabSynchronizationService.class));

    @Test
    @DisplayName("stores the closing moment the client sent rather than a server clock")
    void shouldPassThePushedClosingMomentOnUntouched() {

        final SyncOperationDto closedTab = upsertClosedTab(UUID.randomUUID(), CLOSED_YESTERDAY);
        profileIsTheOwners();
        storedAsRequested();

        syncPushService.applyPushedOperations(push(closedTab), OWNER_ID);

        verify(closedTabSynchronizationService).upsertClosedTabFromPushedOperation(
                eq(closedTab.id()), any(),
                eq(new ClosedTabSynchronizedValuesDto(
                        "https://spring.io", "Spring Boot reference", "https://spring.io/icon.png",
                        CLOSED_YESTERDAY, "Laptop")));
    }

    @Test
    @DisplayName("a closed tab carrying no closing moment is refused rather than stamped with now")
    void shouldRefuseAClosedTabWithNoClosingMoment() {

        final SyncOperationDto momentless = upsertClosedTab(UUID.randomUUID(), null);
        profileIsTheOwners();

        final SyncPushResultDto result =
                syncPushService.applyPushedOperations(push(momentless), OWNER_ID);

        assertThat(result.rejected()).singleElement().satisfies(rejection -> {
            assertThat(rejection.entityKind()).isEqualTo(SyncEntityKind.CLOSED_TAB);
            assertThat(rejection.reason()).isEqualTo(SyncRejectionReason.MISSING_REQUIRED_VALUE);
        });
        verify(closedTabSynchronizationService, never())
                .upsertClosedTabFromPushedOperation(any(), any(), any());
    }

    @Test
    @DisplayName("a closed tab carrying no address is refused, and the rest of the batch is not")
    void shouldRefuseAClosedTabWithNoAddressWithoutAbandoningTheBatch() {

        final SyncOperationDto addressless = new SyncOperationDto(
                SyncOperationKind.UPSERT, SyncEntityKind.CLOSED_TAB, UUID.randomUUID(),
                PROFILE_ID, null, null, null,
                null, null, "Spring Boot reference", null, null,
                null, null, null, null, null, null, null,
                CLOSED_YESTERDAY, "Laptop", null);
        final SyncOperationDto sound = upsertClosedTab(UUID.randomUUID(), CLOSED_YESTERDAY);

        profileIsTheOwners();
        storedAsRequested();

        final SyncPushResultDto result =
                syncPushService.applyPushedOperations(push(addressless, sound), OWNER_ID);

        assertThat(result.rejected()).singleElement().satisfies(rejection -> {
            assertThat(rejection.index()).isZero();
            assertThat(rejection.reason()).isEqualTo(SyncRejectionReason.MISSING_REQUIRED_VALUE);
        });
        verify(closedTabSynchronizationService, times(1))
                .upsertClosedTabFromPushedOperation(eq(sound.id()), any(), any());
    }

    @Test
    @DisplayName("a closed tab naming a profile this account does not have is refused")
    void shouldRefuseAClosedTabWhoseProfileIsNotTheOwners() {

        final SyncOperationDto orphan = upsertClosedTab(UUID.randomUUID(), CLOSED_YESTERDAY);
        when(profileSynchronizationService.findProfileEntityForOwner(PROFILE_ID, OWNER_ID))
                .thenReturn(Optional.empty());

        final SyncPushResultDto result =
                syncPushService.applyPushedOperations(push(orphan), OWNER_ID);

        assertThat(result.rejected()).singleElement().satisfies(rejection ->
                assertThat(rejection.reason()).isEqualTo(SyncRejectionReason.PARENT_NOT_FOUND));
    }

    @Test
    @DisplayName("a closed tab naming no profile at all is refused")
    void shouldRefuseAClosedTabWithNoProfile() {

        final SyncOperationDto unparented = new SyncOperationDto(
                SyncOperationKind.UPSERT, SyncEntityKind.CLOSED_TAB, UUID.randomUUID(),
                null, null, null, null,
                null, null, "Spring Boot reference", "https://spring.io", null,
                null, null, null, null, null, null, null,
                CLOSED_YESTERDAY, "Laptop", null);

        final SyncPushResultDto result =
                syncPushService.applyPushedOperations(push(unparented), OWNER_ID);

        assertThat(result.rejected()).singleElement().satisfies(rejection ->
                assertThat(rejection.reason())
                        .isEqualTo(SyncRejectionReason.MISSING_REQUIRED_VALUE));
    }

    @Test
    @DisplayName("a closed tab that omits its optional fields stores an empty title and no "
            + "favicon or device")
    void shouldStoreAnOmittedTitleAsEmptyAndTheOtherOptionalFieldsAsNull() {

        // A page that never named itself is an ordinary page, so an absent title is not a broken
        // operation - but the column is NOT NULL and the extension models it as a string it
        // always has, so it is stored as empty rather than as null.
        final SyncOperationDto bare = new SyncOperationDto(
                SyncOperationKind.UPSERT, SyncEntityKind.CLOSED_TAB, UUID.randomUUID(),
                PROFILE_ID, null, null, null,
                null, null, null, "https://spring.io", null,
                null, null, null, null, null, null, null,
                CLOSED_YESTERDAY, null, null);

        profileIsTheOwners();
        storedAsRequested();

        syncPushService.applyPushedOperations(push(bare), OWNER_ID);

        verify(closedTabSynchronizationService).upsertClosedTabFromPushedOperation(
                eq(bare.id()), any(),
                eq(new ClosedTabSynchronizedValuesDto(
                        "https://spring.io", "", null, CLOSED_YESTERDAY, null)));
    }

    @Test
    @DisplayName("a delete removes the closed tab it names and nothing else")
    void shouldDeleteExactlyTheClosedTabItNames() {

        final UUID prunedTab = UUID.randomUUID();
        final UUID keptTab = UUID.randomUUID();

        profileIsTheOwners();
        storedAsRequested();

        syncPushService.applyPushedOperations(
                push(upsertClosedTab(keptTab, CLOSED_YESTERDAY), deleteClosedTab(prunedTab)),
                OWNER_ID);

        verify(closedTabSynchronizationService)
                .deleteClosedTabFromPushedOperationIfPresent(prunedTab, OWNER_ID);
        verify(closedTabSynchronizationService, never())
                .deleteClosedTabFromPushedOperationIfPresent(eq(keptTab), any());
    }

    @Test
    @DisplayName("a delete of a closed tab that is already gone is not reported as a refusal")
    void shouldAcceptADeleteOfSomethingAlreadyGone() {

        // The extension prunes its list and pushes what it drops, and an offline queue is
        // replayed at least once: the same delete arriving twice is ordinary traffic.
        final UUID alreadyGone = UUID.randomUUID();
        when(closedTabSynchronizationService
                .deleteClosedTabFromPushedOperationIfPresent(alreadyGone, OWNER_ID))
                .thenReturn(false);

        final SyncPushResultDto result = syncPushService.applyPushedOperations(
                push(deleteClosedTab(alreadyGone)), OWNER_ID);

        assertThat(result.rejected()).isEmpty();
    }

    @Test
    @DisplayName("a delete needs no profile, address or closing moment of its own")
    void shouldNotRequireTheUpsertFieldsOnADelete() {

        final SyncPushResultDto result = syncPushService.applyPushedOperations(
                push(deleteClosedTab(UUID.randomUUID())), OWNER_ID);

        assertThat(result.rejected()).isEmpty();
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
     * Makes the synchronization service answer with a row stored under the requested identifier,
     * which is what an ordinary push of a newly closed tab does.
     */
    private void storedAsRequested() {
        when(closedTabSynchronizationService.upsertClosedTabFromPushedOperation(
                any(), any(), any()))
                .thenAnswer(invocation -> {
                    final ClosedTabEntity storedClosedTab = mock(ClosedTabEntity.class);
                    when(storedClosedTab.getId()).thenReturn(invocation.getArgument(0));
                    return storedClosedTab;
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
     * Builds a complete pushed closed tab upsert - the shape the extension sends when a tab is
     * closed.
     *
     * @param id       identifier of the closed tab
     * @param closedAt closing moment to send, {@code null} to exercise a refusal
     * @return the operation
     */
    private SyncOperationDto upsertClosedTab(final UUID id, final Instant closedAt) {
        return new SyncOperationDto(
                SyncOperationKind.UPSERT, SyncEntityKind.CLOSED_TAB, id,
                PROFILE_ID, null, null, null,
                null, null, "Spring Boot reference", "https://spring.io",
                "https://spring.io/icon.png",
                null, null, null, null, null, null, null,
                closedAt, "Laptop", null);
    }

    /**
     * Builds a pushed closed tab delete - what the extension sends for an entry it pruned.
     *
     * @param id identifier of the closed tab to remove
     * @return the operation
     */
    private SyncOperationDto deleteClosedTab(final UUID id) {
        return new SyncOperationDto(
                SyncOperationKind.DELETE, SyncEntityKind.CLOSED_TAB, id,
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null);
    }
}
