package com.kovospace.newtablinks.sync.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.profile.dtos.ProfileSynchronizedValuesDto;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.services.ProfileSynchronizationService;
import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.dtos.SyncOperationKind;
import com.kovospace.newtablinks.sync.dtos.SyncPushRequestDto;
import com.kovospace.newtablinks.sync.dtos.SyncPushResultDto;
import com.kovospace.newtablinks.sync.dtos.SyncRejectionReason;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests what a pushed profile operation must carry, and what happens to the batch around one that
 * does not.
 *
 * <p>Run through a real {@link SyncPushService} rather than against the applier alone, because the
 * behaviour the browser extension depends on is the combination: a profile operation missing a
 * name is refused <em>and reported</em> while the operations either side of it are still applied,
 * so the response is a 200 carrying a refusal rather than a failed push. A client that advances
 * its synchronization baseline on the status code alone would record a rename as stored that never
 * was.</p>
 *
 * @since 0.0.7
 */
class ProfileSyncOperationApplierTest {

    /** The account every operation in this class belongs to. */
    private static final UUID OWNER_ID = UUID.randomUUID();

    /** Installation the batch claims to come from, echoed on the refresh notification. */
    private static final String ORIGIN_DEVICE_ID = "a-device";

    private final ProfileSynchronizationService profileSynchronizationService =
            mock(ProfileSynchronizationService.class);

    private final SyncPushService syncPushService = new SyncPushService(
            List.of(new ProfileSyncOperationApplier(profileSynchronizationService)),
            mock(UserDataChangePublisher.class));

    @Test
    @DisplayName("a profile rename carrying no name is refused, and the rest of the batch is not")
    void shouldRefuseABlankProfileNameWithoutAbandoningTheBatch() {

        final SyncOperationDto blankRename = upsertProfile(UUID.randomUUID(), "   ", 0);
        final SyncOperationDto validRename = upsertProfile(UUID.randomUUID(), "Study", 1);

        storedAsRequested();

        final SyncPushResultDto result = syncPushService.applyPushedOperations(
                push(blankRename, validRename), OWNER_ID);

        assertThat(result.rejected()).singleElement().satisfies(rejection -> {
            assertThat(rejection.index()).isZero();
            assertThat(rejection.id()).isEqualTo(blankRename.id());
            assertThat(rejection.entityKind()).isEqualTo(SyncEntityKind.PROFILE);
            assertThat(rejection.reason()).isEqualTo(SyncRejectionReason.MISSING_REQUIRED_VALUE);
        });

        // Only the valid one reached the service; the batch was not abandoned because of the other.
        verify(profileSynchronizationService, times(1))
                .upsertProfileFromPushedOperation(eq(validRename.id()), eq(OWNER_ID), any());
    }

    @Test
    @DisplayName("a profile rename carrying no position is refused rather than defaulted")
    void shouldRefuseAProfileOperationWithNoPosition() {

        final SyncOperationDto positionless = upsertProfile(UUID.randomUUID(), "Study", null);

        final SyncPushResultDto result =
                syncPushService.applyPushedOperations(push(positionless), OWNER_ID);

        assertThat(result.rejected()).singleElement().satisfies(rejection ->
                assertThat(rejection.reason())
                        .isEqualTo(SyncRejectionReason.MISSING_REQUIRED_VALUE));
    }

    @Test
    @DisplayName("a profile rename passes the pushed name and position on unchanged")
    void shouldPassTheRenamedNameAndPositionOn() {

        final SyncOperationDto rename = upsertProfile(UUID.randomUUID(), "Work and study", 3);
        storedAsRequested();

        syncPushService.applyPushedOperations(push(rename), OWNER_ID);

        verify(profileSynchronizationService).upsertProfileFromPushedOperation(
                rename.id(), OWNER_ID, new ProfileSynchronizedValuesDto("Work and study", 3));
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Makes the synchronization service answer with a profile stored under the requested
     * identifier, which is what an ordinary rename does.
     */
    private void storedAsRequested() {
        when(profileSynchronizationService.upsertProfileFromPushedOperation(any(), any(), any()))
                .thenAnswer(invocation -> {
                    final ProfileEntity storedProfile =
                            new ProfileEntity(mock(UserEntity.class), "stored", 0);
                    storedProfile.setId(invocation.getArgument(0));
                    return storedProfile;
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
     * Builds a pushed profile upsert - the shape the extension sends a rename as.
     *
     * @param id       identifier of the profile
     * @param name     name to store, may be blank to exercise a refusal
     * @param position display position to store, may be {@code null} to exercise a refusal
     * @return the operation
     */
    private SyncOperationDto upsertProfile(
            final UUID id, final String name, final Integer position) {

        return new SyncOperationDto(
                SyncOperationKind.UPSERT, SyncEntityKind.PROFILE, id,
                null, null, null, null,
                name, null, null, null, null,
                null, null, null, position);
    }
}
