package com.kovospace.newtablinks.sync.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.kovospace.newtablinks.common.MigratedPostgresDatabase;
import com.kovospace.newtablinks.sync.dtos.SyncPushRequestDto;
import com.kovospace.newtablinks.sync.dtos.SyncPushResultDto;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Favourites through a whole synchronisation, as the extension sees it on the wire.
 *
 * <p>The push is read from JSON and the snapshot written back to JSON with the application's own
 * mapper, against the schema the migration image builds - so the field names, the columns and
 * the rule that an absent flag means {@code false} are all checked at once. Both flags are the
 * kind an upsert replaces wholesale: leaving one out is turning it off, never "keep what is
 * stored".</p>
 */
@SpringBootTest
class FavoritesSyncRoundTripAgainstMigratedSchemaTest {

    private static MigratedPostgresDatabase database;

    @Autowired
    private SyncPushService syncPushService;

    @Autowired
    private SyncSnapshotService syncSnapshotService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JsonMapper jsonMapper;

    /**
     * Starts PostgreSQL and runs the migration image against it, before the application starts.
     */
    @BeforeAll
    static void startDatabaseBuiltByTheMigrationImage() {
        database = MigratedPostgresDatabase.startOrSkip();
    }

    /**
     * Stops the containers.
     */
    @AfterAll
    static void stopDatabase() {
        MigratedPostgresDatabase.stop(database);
    }

    /**
     * Points the application at the migrated database.
     *
     * @param registry the property registry
     */
    @DynamicPropertySource
    static void useTheMigratedDatabase(final DynamicPropertyRegistry registry) {
        database.registerDataSource(registry);
    }

    @Test
    @DisplayName("a pushed favourite and a profile with favourites on come back in the snapshot; "
            + "a link that does not say is not a favourite")
    void shouldCarryFavoritesFromThePushIntoTheSnapshot() {
        final Account account = newAccount();

        pushJson(account, account.hierarchyJson(
                "\"enableFavorites\": true,",
                "\"favorite\": true,",
                ""));

        final JsonNode snapshot = snapshotJson(account);
        assertThat(profileOf(snapshot, account.profileId()).get("enableFavorites").asBoolean())
                .isTrue();
        assertThat(linkOf(snapshot, account.favoriteLinkId()).get("favorite").asBoolean())
                .isTrue();
        assertThat(linkOf(snapshot, account.plainLinkId()).get("favorite").isBoolean()).isTrue();
        assertThat(linkOf(snapshot, account.plainLinkId()).get("favorite").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("an upsert that leaves either flag out turns it off, like every other flag an "
            + "upsert replaces")
    void shouldTurnBothFlagsOffWhenAnUpsertOmitsThem() {
        final Account account = newAccount();
        pushJson(account, account.hierarchyJson(
                "\"enableFavorites\": true,",
                "\"favorite\": true,",
                "\"favorite\": true,"));

        pushJson(account, account.hierarchyJson("", "", ""));

        final JsonNode snapshot = snapshotJson(account);
        assertThat(profileOf(snapshot, account.profileId()).get("enableFavorites").asBoolean())
                .isFalse();
        assertThat(linkOf(snapshot, account.favoriteLinkId()).get("favorite").asBoolean())
                .isFalse();
        assertThat(linkOf(snapshot, account.plainLinkId()).get("favorite").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * An account and the identifiers of the records pushed into it.
     *
     * @param ownerId        identifier of the account
     * @param profileId      its profile
     * @param workspaceId    the workspace in that profile
     * @param groupId        the group in that workspace
     * @param favoriteLinkId the link the tests mark as a favourite
     * @param plainLinkId    the link beside it, which says nothing about being one at first
     */
    private record Account(
            UUID ownerId,
            UUID profileId,
            UUID workspaceId,
            UUID groupId,
            UUID favoriteLinkId,
            UUID plainLinkId) {

        /**
         * Writes a push of the account's whole hierarchy, as the extension sends it.
         *
         * @param profileFlag      JSON member, with its trailing comma, to put on the profile -
         *                         empty to leave the setting out
         * @param favoriteLinkFlag the same for the first link
         * @param plainLinkFlag    the same for the second link
         * @return the push request body
         */
        String hierarchyJson(
                final String profileFlag,
                final String favoriteLinkFlag,
                final String plainLinkFlag) {

            return """
                    {"originDeviceId": "a-device", "operations": [
                      {"op": "upsert", "type": "profile", "id": "%s", %s
                       "name": "Profile", "position": 0},
                      {"op": "upsert", "type": "environment", "id": "%s", "profileId": "%s",
                       "name": "Workspace", "position": 0},
                      {"op": "upsert", "type": "group", "id": "%s", "environmentId": "%s",
                       "name": "Group", "position": 0},
                      {"op": "upsert", "type": "link", "id": "%s", "parentGroupId": "%s", %s
                       "title": "Starred", "url": "https://starred.test", "position": 0},
                      {"op": "upsert", "type": "link", "id": "%s", "parentGroupId": "%s", %s
                       "title": "Plain", "url": "https://plain.test", "position": 1}
                    ]}
                    """.formatted(
                    profileId, profileFlag,
                    workspaceId, profileId,
                    groupId, workspaceId,
                    favoriteLinkId, groupId, favoriteLinkFlag,
                    plainLinkId, groupId, plainLinkFlag);
        }
    }

    /**
     * Creates an account with nothing in it yet, and chooses the identifiers it will be given.
     *
     * @return the account
     */
    private Account newAccount() {
        final String unique = UUID.randomUUID().toString().substring(0, 8);
        final UserEntity owner = userRepository.save(new UserEntity("favorites-" + unique,
                unique + "@example.com", null, "Favorites", UserAccountStatus.ACTIVE));
        return new Account(owner.getId(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    /**
     * Reads a push request from JSON and applies it as the account, expecting nothing refused.
     *
     * @param account     the account pushing
     * @param requestJson the request body
     */
    private void pushJson(final Account account, final String requestJson) {
        final SyncPushRequestDto request =
                jsonMapper.readValue(requestJson, SyncPushRequestDto.class);
        final SyncPushResultDto result =
                syncPushService.applyPushedOperations(request, account.ownerId());
        assertThat(result.rejected()).isEmpty();
        assertThat(result.remaps()).isEmpty();
    }

    /**
     * Captures the account's snapshot and writes it as the client would receive it.
     *
     * @param account the account
     * @return the snapshot as a JSON tree
     */
    private JsonNode snapshotJson(final Account account) {
        return jsonMapper.valueToTree(
                syncSnapshotService.captureSnapshotForUser(account.ownerId()));
    }

    private static JsonNode profileOf(final JsonNode snapshot, final UUID profileId) {
        return recordIn(snapshot.get("profiles"), profileId);
    }

    private static JsonNode linkOf(final JsonNode snapshot, final UUID linkId) {
        return recordIn(snapshot.get("links"), linkId);
    }

    /**
     * Finds the record with the given identifier in one of the snapshot's collections.
     *
     * @param collection the collection
     * @param id         the identifier
     * @return the record
     * @throws AssertionError when the collection does not hold it
     */
    private static JsonNode recordIn(final JsonNode collection, final UUID id) {
        for (final JsonNode candidate : collection) {
            if (id.toString().equals(candidate.get("id").asString())) {
                return candidate;
            }
        }
        throw new AssertionError("No record " + id + " in " + collection);
    }
}
