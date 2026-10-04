package com.kovospace.newtablinks.sync.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.closedtab.services.ClosedTabSynchronizationService;
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
 * <p>The profile's settings are read here too. They are boxed on the wire so that an operation
 * about another kind of record can leave them out, and unboxing them is where a pushed
 * {@code true} would be lost - or where the flag next to them, whether the subgroup's or the
 * profile's other one, would be read instead.</p>
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
            mock(UserDataChangePublisher.class),
            PermissiveSyncPushLimitGuard.create(),
            mock(ClosedTabSynchronizationService.class));

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
                rename.id(), OWNER_ID,
                new ProfileSynchronizedValuesDto("Work and study", false, false, List.of(), 3));
    }

    @Test
    @DisplayName("a pushed profile setting is carried through to the profile being stored")
    void shouldPassThePushedDragAndDropSettingOn() {

        final SyncOperationDto settingsChange =
                upsertProfile(UUID.randomUUID(), "Work", 0, Boolean.TRUE);
        storedAsRequested();

        syncPushService.applyPushedOperations(push(settingsChange), OWNER_ID);

        verify(profileSynchronizationService).upsertProfileFromPushedOperation(
                settingsChange.id(), OWNER_ID,
                new ProfileSynchronizedValuesDto("Work", true, false, List.of(), 0));
    }

    @Test
    @DisplayName("a profile upsert that omits the setting switches it off rather than keeping it")
    void shouldTreatAnOmittedDragAndDropSettingAsOff() {

        // An extension build from before the settings dialog sends no such field, and an upsert
        // replaces every synchronized field: there is no "unchanged" to express, exactly as with
        // the subgroup's collapse flags.
        final SyncOperationDto rename = upsertProfile(UUID.randomUUID(), "Work", 0, null);
        storedAsRequested();

        syncPushService.applyPushedOperations(push(rename), OWNER_ID);

        verify(profileSynchronizationService).upsertProfileFromPushedOperation(
                rename.id(), OWNER_ID,
                new ProfileSynchronizedValuesDto("Work", false, false, List.of(), 0));
    }

    @Test
    @DisplayName("a profile does not take its setting from the subgroup's tab group flag")
    void shouldNotReadTheSubgroupTabGroupFlagAsTheProfileSetting() {

        final SyncOperationDto confusable =
                upsertProfileCarryingTheSubgroupTabGroupFlag(UUID.randomUUID(), "Work", 0);
        storedAsRequested();

        syncPushService.applyPushedOperations(push(confusable), OWNER_ID);

        verify(profileSynchronizationService).upsertProfileFromPushedOperation(
                confusable.id(), OWNER_ID,
                new ProfileSynchronizedValuesDto("Work", false, false, List.of(), 0));
    }

    @Test
    @DisplayName("a pushed tips setting is carried through to the profile being stored")
    void shouldPassThePushedHideTipsSettingOn() {

        final SyncOperationDto tipsDismissed =
                upsertProfileHidingTips(UUID.randomUUID(), "Work", 0, Boolean.TRUE);
        storedAsRequested();

        syncPushService.applyPushedOperations(push(tipsDismissed), OWNER_ID);

        verify(profileSynchronizationService).upsertProfileFromPushedOperation(
                tipsDismissed.id(), OWNER_ID,
                new ProfileSynchronizedValuesDto("Work", false, true, List.of(), 0));
    }

    @Test
    @DisplayName("a profile upsert that omits the tips setting shows the tips rather than keeping "
            + "them hidden")
    void shouldTreatAnOmittedHideTipsSettingAsOff() {

        // An extension build from before the tips sends no such field, and an upsert replaces
        // every synchronized field: there is no "unchanged" to express. Off means visible, which
        // is why the flag is named for hiding.
        final SyncOperationDto rename = upsertProfileHidingTips(UUID.randomUUID(), "Work", 0, null);
        storedAsRequested();

        syncPushService.applyPushedOperations(push(rename), OWNER_ID);

        verify(profileSynchronizationService).upsertProfileFromPushedOperation(
                rename.id(), OWNER_ID,
                new ProfileSynchronizedValuesDto("Work", false, false, List.of(), 0));
    }

    @Test
    @DisplayName("the tips a profile dismissed one by one are carried through, in order")
    void shouldPassThePushedDismissedTipsOnInOrder() {

        final SyncOperationDto twoDismissed = upsertProfileDismissingTips(
                UUID.randomUUID(), "Work", 0, List.of("hide-tips", "change-background"));
        storedAsRequested();

        syncPushService.applyPushedOperations(push(twoDismissed), OWNER_ID);

        verify(profileSynchronizationService).upsertProfileFromPushedOperation(
                twoDismissed.id(), OWNER_ID,
                new ProfileSynchronizedValuesDto(
                        "Work", false, false, List.of("hide-tips", "change-background"), 0));
    }

    @Test
    @DisplayName("a profile upsert that omits the dismissed tips stores none rather than keeping "
            + "the old ones")
    void shouldTreatOmittedDismissedTipsAsNone() {

        // An extension build from before single tips could be dismissed sends no such field,
        // and an upsert replaces every synchronized field - as with an omitted flag.
        final SyncOperationDto rename =
                upsertProfileDismissingTips(UUID.randomUUID(), "Work", 0, null);
        storedAsRequested();

        syncPushService.applyPushedOperations(push(rename), OWNER_ID);

        verify(profileSynchronizationService).upsertProfileFromPushedOperation(
                rename.id(), OWNER_ID,
                new ProfileSynchronizedValuesDto("Work", false, false, List.of(), 0));
    }

    @Test
    @DisplayName("a profile does not take its tips setting from the drag and drop setting beside "
            + "it")
    void shouldNotReadTheDragAndDropSettingAsTheTipsSetting() {

        // The two profile settings are adjacent booleans of the same flat operation, so reading
        // the wrong one is the mistake that would still look like working code.
        final SyncOperationDto draggableOnly =
                upsertProfile(UUID.randomUUID(), "Work", 0, Boolean.TRUE);
        storedAsRequested();

        syncPushService.applyPushedOperations(push(draggableOnly), OWNER_ID);

        verify(profileSynchronizationService).upsertProfileFromPushedOperation(
                draggableOnly.id(), OWNER_ID,
                new ProfileSynchronizedValuesDto("Work", true, false, List.of(), 0));
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
     * Builds a pushed profile upsert that says nothing about the profile's settings - the shape
     * an extension build from before they existed sends a rename as.
     *
     * @param id       identifier of the profile
     * @param name     name to store, may be blank to exercise a refusal
     * @param position display position to store, may be {@code null} to exercise a refusal
     * @return the operation
     */
    private SyncOperationDto upsertProfile(
            final UUID id, final String name, final Integer position) {

        return upsertProfile(id, name, position, null);
    }

    /**
     * Builds a pushed profile upsert carrying the drag and drop setting and no other settings
     * flag.
     *
     * @param id                identifier of the profile
     * @param name              name to store
     * @param position          display position to store
     * @param enableDragAndDrop the drag and drop setting as the operation carries it, {@code null}
     *                          when the operation leaves it out
     * @return the operation
     */
    private SyncOperationDto upsertProfile(
            final UUID id,
            final String name,
            final Integer position,
            final Boolean enableDragAndDrop) {

        return new SyncOperationDto(
                SyncOperationKind.UPSERT, SyncEntityKind.PROFILE, id,
                null, null, null, null,
                name, null, null, null, null,
                null, null, null, enableDragAndDrop, null, null, null,
                null, null, position);
    }

    /**
     * Builds a pushed profile upsert carrying the dismissed tips and no settings flag.
     *
     * @param id            identifier of the profile
     * @param name          name to store
     * @param position      display position to store
     * @param dismissedTips the dismissed tip identifiers as the operation carries them,
     *                      {@code null} when the operation leaves them out
     * @return the operation
     */
    private SyncOperationDto upsertProfileDismissingTips(
            final UUID id,
            final String name,
            final Integer position,
            final List<String> dismissedTips) {

        return new SyncOperationDto(
                SyncOperationKind.UPSERT, SyncEntityKind.PROFILE, id,
                null, null, null, null,
                name, null, null, null, null,
                null, null, null, null, null, dismissedTips, null,
                null, null, position);
    }

    /**
     * Builds a pushed profile upsert carrying the tips setting and no other settings flag.
     *
     * @param id       identifier of the profile
     * @param name     name to store
     * @param position display position to store
     * @param hideTips the tips setting as the operation carries it, {@code null} when the
     *                 operation leaves it out
     * @return the operation
     */
    private SyncOperationDto upsertProfileHidingTips(
            final UUID id,
            final String name,
            final Integer position,
            final Boolean hideTips) {

        return new SyncOperationDto(
                SyncOperationKind.UPSERT, SyncEntityKind.PROFILE, id,
                null, null, null, null,
                name, null, null, null, null,
                null, null, null, null, hideTips, null, null,
                null, null, position);
    }

    /**
     * Builds a pushed profile upsert carrying the <em>subgroup's</em> tab group flag and no
     * settings flag of its own.
     *
     * <p>Only meaningful because {@link SyncOperationDto} is one flat shape covering every kind of
     * record: both booleans are present on a profile operation, and reading the wrong one would
     * look like working code.</p>
     *
     * @param id       identifier of the profile
     * @param name     name to store
     * @param position display position to store
     * @return the operation
     */
    private SyncOperationDto upsertProfileCarryingTheSubgroupTabGroupFlag(
            final UUID id, final String name, final Integer position) {

        return new SyncOperationDto(
                SyncOperationKind.UPSERT, SyncEntityKind.PROFILE, id,
                null, null, null, null,
                name, null, null, null, null,
                null, null, Boolean.TRUE, null, null, null, null,
                null, null, position);
    }
}
