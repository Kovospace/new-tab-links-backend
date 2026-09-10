package com.kovospace.newtablinks.profile.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.common.services.HierarchyDeletionService;
import com.kovospace.newtablinks.profile.dtos.ProfileSynchronizedValuesDto;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.repositories.ProfileRepository;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.services.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests what a pushed profile upsert does to a profile the account already has, and to one it does
 * not.
 *
 * <p>Covered because the browser extension's "rename profile" action is built on the answer. It
 * sends a rename as an ordinary {@code upsert} of an identifier it believes the server already
 * holds, and therefore depends on two things this class pins: that such an upsert edits the
 * existing row rather than attempting an insert, and that when the identifier turns out not to be
 * the account's after all the row is <em>recreated under a new identifier</em> instead of being
 * edited - which the extension has to notice, because the profile it thought it was renaming is
 * gone and the replacement is empty.</p>
 *
 * <p>It also pins what an upsert does to the profile's settings, which ride on the same row: they
 * are replaced like every other synchronized field, so a pushed {@code false} switches one off
 * rather than meaning "no opinion".</p>
 *
 * <p>What this class cannot show is the foreign key behaviour, which lives in the migration
 * repository rather than here. It shows that a rename issues no delete and no cascade of its own;
 * the schema's part is a separate concern.</p>
 *
 * @since 0.0.7
 */
class ProfileSynchronizationServiceTest {

    /** The account every operation in this class belongs to. */
    private static final UUID OWNER_ID = UUID.randomUUID();

    /** Name the profile carries before the rename under test. */
    private static final String NAME_BEFORE_RENAME = "Work";

    /** Name the pushed operation renames it to. */
    private static final String NAME_AFTER_RENAME = "Work and study";

    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final UserService userService = mock(UserService.class);
    private final UserDataChangePublisher userDataChangePublisher =
            mock(UserDataChangePublisher.class);
    private final HierarchyDeletionService hierarchyDeletionService =
            mock(HierarchyDeletionService.class);

    private final ProfileSynchronizationService profileSynchronizationService =
            new ProfileSynchronizationService(
                    profileRepository, userService, userDataChangePublisher,
                    hierarchyDeletionService);

    @Test
    @DisplayName("renames a profile the account already owns by editing the row it already has")
    void shouldRenameAnExistingProfileInPlace() {

        final UUID profileId = UUID.randomUUID();
        final ProfileEntity existingProfile = anExistingProfile(profileId, NAME_BEFORE_RENAME, 2);

        final ProfileEntity storedProfile = profileSynchronizationService
                .upsertProfileFromPushedOperation(
                        profileId, OWNER_ID, valuesRenaming(NAME_AFTER_RENAME, 2));

        assertThat(storedProfile).isSameAs(existingProfile);
        assertThat(storedProfile.getName()).isEqualTo(NAME_AFTER_RENAME);
        // Nothing is inserted: the managed entity is edited and the transaction flushes it.
        verify(profileRepository, never()).save(any());
        // No owner is resolved either, which only an insert needs.
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("a rename keeps the identifier, so the client is told of no remapping")
    void shouldKeepTheIdentifierOfARenamedProfile() {

        final UUID profileId = UUID.randomUUID();
        anExistingProfile(profileId, NAME_BEFORE_RENAME, 0);

        final ProfileEntity storedProfile = profileSynchronizationService
                .upsertProfileFromPushedOperation(
                        profileId, OWNER_ID, valuesRenaming(NAME_AFTER_RENAME, 0));

        // SyncPushService reports a remapping by comparing these two; equal means none is reported.
        assertThat(storedProfile.getId()).isEqualTo(profileId);
    }

    @Test
    @DisplayName("a pushed upsert sets the display position as well as the name")
    void shouldOverwriteTheDisplayPositionOfARenamedProfile() {

        final UUID profileId = UUID.randomUUID();
        final ProfileEntity existingProfile = anExistingProfile(profileId, NAME_BEFORE_RENAME, 2);

        profileSynchronizationService.upsertProfileFromPushedOperation(
                profileId, OWNER_ID, valuesRenaming(NAME_AFTER_RENAME, 5));

        // Deliberate, and the reason a rename must send the position the server already holds
        // unless a reorder is actually intended: an upsert replaces every synchronized field.
        assertThat(existingProfile.getPosition()).isEqualTo(5);
    }

    @Test
    @DisplayName("renaming a profile removes nothing beneath it")
    void shouldNotTouchAnythingBeneathARenamedProfile() {

        final UUID profileId = UUID.randomUUID();
        anExistingProfile(profileId, NAME_BEFORE_RENAME, 0);

        profileSynchronizationService.upsertProfileFromPushedOperation(
                profileId, OWNER_ID, valuesRenaming(NAME_AFTER_RENAME, 0));

        // The only collaborator that reaches a profile's environments, groups and links.
        verifyNoInteractions(hierarchyDeletionService);
        verify(profileRepository, never()).delete(any());
    }

    @Test
    @DisplayName("announces the rename, so the account's other browsers re-read")
    void shouldAnnounceARenameToTheOwnersOtherBrowsers() {

        final UUID profileId = UUID.randomUUID();
        anExistingProfile(profileId, NAME_BEFORE_RENAME, 0);

        profileSynchronizationService.upsertProfileFromPushedOperation(
                profileId, OWNER_ID, valuesRenaming(NAME_AFTER_RENAME, 0));

        verify(userDataChangePublisher).publishChangeFor(OWNER_ID);
    }

    @Test
    @DisplayName("stores a profile the account does not have yet under the identifier it asked for")
    void shouldInsertUnderTheClientIdentifierWhenItIsFree() {

        final UUID profileId = UUID.randomUUID();
        anAccountWithoutThatProfile(profileId);
        when(profileRepository.existsById(profileId)).thenReturn(false);

        final ProfileEntity storedProfile = profileSynchronizationService
                .upsertProfileFromPushedOperation(
                        profileId, OWNER_ID, valuesRenaming(NAME_AFTER_RENAME, 0));

        assertThat(storedProfile.getId()).isEqualTo(profileId);
        assertThat(storedProfile.getName()).isEqualTo(NAME_AFTER_RENAME);
        verify(profileRepository).save(any(ProfileEntity.class));
    }

    @Test
    @DisplayName("a rename of a profile this account no longer has creates a new one instead")
    void shouldResurrectUnderANewIdentifierWhenTheProfileIsNotTheAccountsAnyMore() {

        final UUID profileId = UUID.randomUUID();
        anAccountWithoutThatProfile(profileId);
        // The row exists - it is another account's, or this account's own already deleted one
        // whose identifier the extension is still holding.
        when(profileRepository.existsById(profileId)).thenReturn(true);

        final ProfileEntity storedProfile = profileSynchronizationService
                .upsertProfileFromPushedOperation(
                        profileId, OWNER_ID, valuesRenaming(NAME_AFTER_RENAME, 0));

        // Not an in-place rename and not a refusal: a brand new, empty profile, whose changed
        // identifier is what the client is told about as a remapping.
        assertThat(storedProfile.getId()).isNotEqualTo(profileId);
        assertThat(storedProfile.getName()).isEqualTo(NAME_AFTER_RENAME);
        verify(profileRepository).save(any(ProfileEntity.class));
    }

    @Test
    @DisplayName("a pushed upsert stores the profile's drag and drop setting")
    void shouldStoreTheDragAndDropSettingOfAnExistingProfile() {

        final UUID profileId = UUID.randomUUID();
        final ProfileEntity existingProfile = anExistingProfile(profileId, NAME_BEFORE_RENAME, 0);

        profileSynchronizationService.upsertProfileFromPushedOperation(
                profileId, OWNER_ID,
                new ProfileSynchronizedValuesDto(NAME_AFTER_RENAME, true, 0));

        assertThat(existingProfile.isEnableDragAndDrop()).isTrue();
    }

    @Test
    @DisplayName("a pushed upsert switches the drag and drop setting back off again")
    void shouldClearTheDragAndDropSettingWhenTheUpsertCarriesItOff() {

        final UUID profileId = UUID.randomUUID();
        final ProfileEntity existingProfile = anExistingProfile(profileId, NAME_BEFORE_RENAME, 0);
        existingProfile.setEnableDragAndDrop(true);

        profileSynchronizationService.upsertProfileFromPushedOperation(
                profileId, OWNER_ID,
                new ProfileSynchronizedValuesDto(NAME_AFTER_RENAME, false, 0));

        // The whole point of an upsert replacing every synchronized field: switching the setting
        // off is a change like any other, and a device that never learns of it keeps dragging.
        assertThat(existingProfile.isEnableDragAndDrop()).isFalse();
    }

    @Test
    @DisplayName("a profile inserted from a push keeps the setting the push carried")
    void shouldStoreTheDragAndDropSettingOnAnInsertedProfile() {

        final UUID profileId = UUID.randomUUID();
        anAccountWithoutThatProfile(profileId);
        when(profileRepository.existsById(profileId)).thenReturn(false);

        final ProfileEntity storedProfile = profileSynchronizationService
                .upsertProfileFromPushedOperation(
                        profileId, OWNER_ID,
                        new ProfileSynchronizedValuesDto(NAME_AFTER_RENAME, true, 0));

        assertThat(storedProfile.isEnableDragAndDrop()).isTrue();
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Builds the values of a plain rename - one that changes nothing about the settings.
     *
     * @param name     name to store
     * @param position display position to store
     * @return the values a pushed upsert would carry
     */
    private ProfileSynchronizedValuesDto valuesRenaming(final String name, final int position) {
        return new ProfileSynchronizedValuesDto(name, false, position);
    }

    /**
     * Makes the repository answer with a profile the account owns.
     *
     * @param profileId identifier the profile is stored under
     * @param name      name it carries before the operation under test
     * @param position  display position it carries before the operation under test
     * @return the entity the repository will return
     */
    private ProfileEntity anExistingProfile(
            final UUID profileId, final String name, final int position) {

        final ProfileEntity existingProfile =
                new ProfileEntity(mock(UserEntity.class), name, position);
        existingProfile.setId(profileId);

        when(profileRepository.findByIdAndOwnerId(profileId, OWNER_ID))
                .thenReturn(Optional.of(existingProfile));

        return existingProfile;
    }

    /**
     * Makes the repository answer that the account has no profile under that identifier, and
     * lets the insert path resolve an owner and save.
     *
     * @param profileId identifier the client asked for
     */
    private void anAccountWithoutThatProfile(final UUID profileId) {
        when(profileRepository.findByIdAndOwnerId(profileId, OWNER_ID)).thenReturn(Optional.empty());
        when(userService.getRequiredUserEntity(OWNER_ID)).thenReturn(mock(UserEntity.class));
        when(profileRepository.save(any(ProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
