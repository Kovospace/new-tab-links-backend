package com.kovospace.newtablinks.sync.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kovospace.newtablinks.common.MigratedPostgresDatabase;
import com.kovospace.newtablinks.common.exceptions.FairUseLimitReachedException;
import com.kovospace.newtablinks.common.models.FairUseLimit;
import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.dtos.SyncOperationKind;
import com.kovospace.newtablinks.sync.dtos.SyncPushRequestDto;
import com.kovospace.newtablinks.sync.dtos.SyncPushResultDto;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The Fair Use Policy caps on the sync push, against the schema the migration image builds.
 *
 * <p>The batch is judged on its end state: a push that grows a collection past its cap fails
 * whole (409), a push that replaces a record at the cap goes through even though the extension
 * sends its upserts before its deletions, an account already over a cap keeps syncing what does
 * not grow it, and closed-tab history is trimmed rather than refused.</p>
 */
@SpringBootTest
class SyncPushFairUseAgainstMigratedSchemaTest {

    private static final int PROFILE_CAP = 2;
    private static final int WORKSPACE_CAP = 2;
    private static final int LINKS_PER_WORKSPACE_CAP = 3;
    private static final int CLOSED_TAB_CAP = 3;

    private static final int UNREACHABLE_FREE_LIMIT = 1_000;

    private static MigratedPostgresDatabase database;

    @Autowired
    private SyncPushService syncPushService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
     * Points the application at the migrated database, with caps small enough to reach.
     *
     * @param registry the property registry
     */
    @DynamicPropertySource
    static void useTheMigratedDatabaseAndSmallCaps(final DynamicPropertyRegistry registry) {
        database.registerDataSource(registry);
        registry.add("newtablinks.fair-use.profiles", () -> PROFILE_CAP);
        registry.add("newtablinks.fair-use.workspaces", () -> WORKSPACE_CAP);
        registry.add("newtablinks.fair-use.links-per-workspace", () -> LINKS_PER_WORKSPACE_CAP);
        // These accounts are free; the free plan's lower limits are FreePlanLimitsAgainst-
        // MigratedSchemaTest's subject, and would otherwise refuse before any fair use cap.
        registry.add("newtablinks.plan-limits.free.profiles", () -> UNREACHABLE_FREE_LIMIT);
        registry.add("newtablinks.plan-limits.free.workspaces", () -> UNREACHABLE_FREE_LIMIT);
        registry.add("newtablinks.fair-use.closed-tab-history", () -> CLOSED_TAB_CAP);
    }

    @Test
    @DisplayName("a batch adding a link to a full workspace is refused whole, with the cap named, "
            + "and the edit beside it is not applied")
    void shouldRefuseTheWholeBatchThatOverfillsAWorkspace() {
        final Account account = newAccountWithOneWorkspace();
        final List<UUID> links = pushLinks(account, LINKS_PER_WORKSPACE_CAP);

        assertThatThrownBy(() -> push(account,
                upsertLink(UUID.randomUUID(), account.groupId(), "New"),
                upsertLink(links.getFirst(), account.groupId(), "Edited")))
                .isInstanceOfSatisfying(FairUseLimitReachedException.class, refusal -> {
                    assertThat(refusal.getLimit()).isEqualTo(FairUseLimit.LINKS_PER_WORKSPACE);
                    assertThat(refusal.getMaximum()).isEqualTo(LINKS_PER_WORKSPACE_CAP);
                });

        assertThat(linkTitle(links.getFirst())).isEqualTo("Link");
        assertThat(countLinksInGroup(account.groupId())).isEqualTo(LINKS_PER_WORKSPACE_CAP);
    }

    @Test
    @DisplayName("replacing a link at the cap goes through, although the upsert comes first")
    void shouldAdmitABatchThatReplacesALinkAtTheCap() {
        final Account account = newAccountWithOneWorkspace();
        final List<UUID> links = pushLinks(account, LINKS_PER_WORKSPACE_CAP);
        final UUID replacement = UUID.randomUUID();

        push(account,
                upsertLink(replacement, account.groupId(), "Replacement"),
                delete(SyncEntityKind.LINK, links.getFirst()));

        assertThat(countLinksInGroup(account.groupId())).isEqualTo(LINKS_PER_WORKSPACE_CAP);
        assertThat(linkTitle(replacement)).isEqualTo("Replacement");
    }

    @Test
    @DisplayName("an account already over the cap keeps syncing edits, deletions and even "
            + "replacements; only a batch that grows it further is refused")
    void shouldKeepSyncingAnAccountAlreadyOverTheCap() {
        final Account account = newAccountWithOneWorkspace();
        final List<UUID> links = pushLinks(account, LINKS_PER_WORKSPACE_CAP);
        insertLinksBehindTheGuardsBack(account.groupId(), 2);

        push(account,
                upsertLink(links.get(0), account.groupId(), "Still editable"),
                upsertLink(UUID.randomUUID(), account.groupId(), "Replacement"),
                delete(SyncEntityKind.LINK, links.get(1)));
        assertThat(linkTitle(links.get(0))).isEqualTo("Still editable");
        assertThat(countLinksInGroup(account.groupId())).isEqualTo(5);

        push(account, delete(SyncEntityKind.LINK, links.get(2)));
        assertThat(countLinksInGroup(account.groupId())).isEqualTo(4);

        assertThatThrownBy(() -> push(account,
                upsertLink(UUID.randomUUID(), account.groupId(), "Growth"),
                upsertLink(UUID.randomUUID(), account.groupId(), "Growth"),
                delete(SyncEntityKind.LINK, links.get(0))))
                .isInstanceOf(FairUseLimitReachedException.class);
        assertThat(countLinksInGroup(account.groupId())).isEqualTo(4);
    }

    @Test
    @DisplayName("a link moving into a full workspace is refused; moving between groups of one "
            + "workspace is not")
    void shouldRefuseALinkMovingIntoAFullWorkspaceOnly() {
        final Account account = newAccountWithOneWorkspace();
        final UUID resident = pushLinks(account, LINKS_PER_WORKSPACE_CAP).getFirst();
        final UUID secondGroupInFullWorkspace = pushGroup(account, account.workspaceId());
        final UUID groupInOtherWorkspace = pushGroup(account, pushWorkspace(account));
        final UUID wanderer = UUID.randomUUID();
        push(account, upsertLink(wanderer, groupInOtherWorkspace, "Wanderer"));

        assertThatThrownBy(() -> push(account, upsertLink(wanderer, account.groupId(), "Wanderer")))
                .isInstanceOfSatisfying(FairUseLimitReachedException.class, refusal ->
                        assertThat(refusal.getLimit()).isEqualTo(FairUseLimit.LINKS_PER_WORKSPACE));

        push(account, upsertLink(resident, secondGroupInFullWorkspace, "Resident"));
        assertThat(countLinksInGroup(secondGroupInFullWorkspace)).isEqualTo(1);
    }

    @Test
    @DisplayName("a group whose links would overfill its new workspace is refused; an empty one "
            + "moves freely")
    void shouldRefuseAGroupWhoseLinksOverfillItsNewWorkspace() {
        final Account account = newAccountWithOneWorkspace();
        pushLinks(account, LINKS_PER_WORKSPACE_CAP);
        final UUID otherWorkspace = pushWorkspace(account);
        final UUID groupWithALink = pushGroup(account, otherWorkspace);
        push(account, upsertLink(UUID.randomUUID(), groupWithALink, "Passenger"));
        final UUID emptyGroup = pushGroup(account, otherWorkspace);

        push(account, upsertGroup(emptyGroup, account.workspaceId()));
        assertThatThrownBy(() -> push(account, upsertGroup(groupWithALink, account.workspaceId())))
                .isInstanceOf(FairUseLimitReachedException.class);
    }

    @Test
    @DisplayName("closed-tab history never refuses a push: the oldest entries beyond the cap are "
            + "deleted, across every profile")
    void shouldTrimTheOldestClosedTabsInsteadOfRefusing() {
        final Account account = newAccountWithOneWorkspace();
        final UUID secondProfile = UUID.randomUUID();
        push(account, upsertProfile(secondProfile));
        final UUID oldest = UUID.randomUUID();
        final UUID secondOldest = UUID.randomUUID();
        final UUID middle = UUID.randomUUID();
        final UUID newer = UUID.randomUUID();
        final UUID newest = UUID.randomUUID();

        push(account,
                upsertClosedTab(middle, account.profileId(), 3),
                upsertClosedTab(oldest, secondProfile, 1),
                upsertClosedTab(secondOldest, account.profileId(), 2));
        final SyncPushResultDto result = push(account,
                upsertClosedTab(newest, secondProfile, 5),
                upsertClosedTab(newer, account.profileId(), 4));

        assertThat(result.rejected()).isEmpty();
        assertThat(closedTabsOf(account.ownerId()))
                .containsExactlyInAnyOrder(middle, newer, newest);
    }

    @Test
    @DisplayName("a new profile or a new workspace beyond its cap fails the push with that cap")
    void shouldRefuseProfilesAndWorkspacesBeyondTheirCaps() {
        final Account account = newAccountWithOneWorkspace();
        final UUID secondProfile = UUID.randomUUID();
        push(account, upsertProfile(secondProfile));
        pushWorkspace(account);

        assertThatThrownBy(() -> push(account, upsertProfile(UUID.randomUUID())))
                .isInstanceOfSatisfying(FairUseLimitReachedException.class, refusal -> {
                    assertThat(refusal.getLimit()).isEqualTo(FairUseLimit.PROFILES);
                    assertThat(refusal.getMaximum()).isEqualTo(PROFILE_CAP);
                });
        assertThatThrownBy(() -> push(account, upsertWorkspace(UUID.randomUUID(), secondProfile)))
                .isInstanceOfSatisfying(FairUseLimitReachedException.class, refusal -> {
                    assertThat(refusal.getLimit()).isEqualTo(FairUseLimit.WORKSPACES);
                    assertThat(refusal.getMaximum()).isEqualTo(WORKSPACE_CAP);
                });
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * An account with one profile, one workspace in it and one group in that.
     *
     * @param ownerId     identifier of the account
     * @param profileId   its profile
     * @param workspaceId its workspace
     * @param groupId     the group in that workspace
     */
    private record Account(UUID ownerId, UUID profileId, UUID workspaceId, UUID groupId) {
    }

    /**
     * Creates an account and pushes its first profile, workspace and group.
     *
     * @return the account
     */
    private Account newAccountWithOneWorkspace() {
        final String unique = UUID.randomUUID().toString().substring(0, 8);
        final UserEntity owner = userRepository.save(new UserEntity("sync-" + unique,
                unique + "@example.com", null, "Sync", UserAccountStatus.ACTIVE));
        final UUID profileId = UUID.randomUUID();
        final UUID workspaceId = UUID.randomUUID();
        final UUID groupId = UUID.randomUUID();
        final Account account = new Account(owner.getId(), profileId, workspaceId, groupId);
        final SyncPushResultDto result = push(account,
                upsertProfile(profileId),
                upsertWorkspace(workspaceId, profileId),
                upsertGroup(groupId, workspaceId));
        assertThat(result.rejected()).isEmpty();
        return account;
    }

    /**
     * Pushes links into the account's first group, one push each.
     *
     * @param account the account
     * @param count   how many
     * @return their identifiers
     */
    private List<UUID> pushLinks(final Account account, final int count) {
        final List<UUID> links = java.util.stream.Stream.generate(UUID::randomUUID)
                .limit(count).toList();
        links.forEach(link -> assertThat(
                push(account, upsertLink(link, account.groupId(), "Link")).rejected()).isEmpty());
        return links;
    }

    /**
     * Pushes a new workspace into the account's profile.
     *
     * @param account the account
     * @return its identifier
     */
    private UUID pushWorkspace(final Account account) {
        final UUID workspaceId = UUID.randomUUID();
        assertThat(push(account, upsertWorkspace(workspaceId, account.profileId())).rejected())
                .isEmpty();
        return workspaceId;
    }

    /**
     * Pushes a new group into a workspace.
     *
     * @param account     the account
     * @param workspaceId the workspace
     * @return its identifier
     */
    private UUID pushGroup(final Account account, final UUID workspaceId) {
        final UUID groupId = UUID.randomUUID();
        assertThat(push(account, upsertGroup(groupId, workspaceId)).rejected()).isEmpty();
        return groupId;
    }

    /**
     * Pushes a batch as the account.
     *
     * @param account    the account
     * @param operations the batch
     * @return what the push reported
     */
    private SyncPushResultDto push(final Account account, final SyncOperationDto... operations) {
        return syncPushService.applyPushedOperations(
                new SyncPushRequestDto("a-device", List.of(operations)), account.ownerId());
    }

    private static SyncOperationDto upsertProfile(final UUID id) {
        return new SyncOperationDto(SyncOperationKind.UPSERT, SyncEntityKind.PROFILE, id,
                null, null, null, null, "Profile", null, null, null, null,
                null, null, null, null, null, null, null, null, null, 0);
    }

    private static SyncOperationDto upsertWorkspace(final UUID id, final UUID profileId) {
        return new SyncOperationDto(SyncOperationKind.UPSERT, SyncEntityKind.ENVIRONMENT, id,
                profileId, null, null, null, "Workspace", null, null, null, null,
                null, null, null, null, null, null, null, null, null, 0);
    }

    private static SyncOperationDto upsertGroup(final UUID id, final UUID workspaceId) {
        return new SyncOperationDto(SyncOperationKind.UPSERT, SyncEntityKind.GROUP, id,
                null, workspaceId, null, null, "Group", null, null, null, null,
                null, null, null, null, null, null, null, null, null, 0);
    }

    private static SyncOperationDto upsertLink(final UUID id, final UUID groupId, final String title) {
        return new SyncOperationDto(SyncOperationKind.UPSERT, SyncEntityKind.LINK, id,
                null, null, groupId, null, null, null, title, "https://example.test", null,
                null, null, null, null, null, null, null, null, null, 0);
    }

    private static SyncOperationDto upsertClosedTab(
            final UUID id,
            final UUID profileId,
            final int minutesAfterTen) {

        return new SyncOperationDto(SyncOperationKind.UPSERT, SyncEntityKind.CLOSED_TAB, id,
                profileId, null, null, null, null, null, "Closed", "https://closed.test", null,
                null, null, null, null, null, null, null,
                Instant.parse("2026-10-01T10:00:00Z").plusSeconds(60L * minutesAfterTen),
                null, null);
    }

    private static SyncOperationDto delete(final SyncEntityKind kind, final UUID id) {
        return new SyncOperationDto(SyncOperationKind.DELETE, kind, id,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Puts links into a group without asking the guard, as data created before the cap was.
     *
     * @param groupId the group
     * @param count   how many
     */
    private void insertLinksBehindTheGuardsBack(final UUID groupId, final int count) {
        for (int index = 0; index < count; index++) {
            jdbcTemplate.update("INSERT INTO link (id, group_id, title, url, position, created_at, "
                            + "updated_at) VALUES (?, ?, 'Old', 'https://old.test', ?, now(), now())",
                    UUID.randomUUID(), groupId, 100 + index);
        }
    }

    private String linkTitle(final UUID linkId) {
        return jdbcTemplate.queryForObject("SELECT title FROM link WHERE id = ?", String.class, linkId);
    }

    private int countLinksInGroup(final UUID groupId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM link WHERE group_id = ?", Integer.class, groupId);
    }

    private List<UUID> closedTabsOf(final UUID ownerId) {
        return jdbcTemplate.queryForList("SELECT tab.id FROM closed_tab tab "
                + "JOIN profile ON profile.id = tab.profile_id WHERE profile.user_id = ?",
                UUID.class, ownerId);
    }
}
