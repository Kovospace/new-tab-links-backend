package com.kovospace.newtablinks.common.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kovospace.newtablinks.auth.services.AccessTokenIssuer;
import com.kovospace.newtablinks.common.MigratedPostgresDatabase;
import com.kovospace.newtablinks.common.exceptions.FairUseLimitReachedException;
import com.kovospace.newtablinks.common.models.FairUseLimit;
import com.kovospace.newtablinks.environment.dtos.EnvironmentDto;
import com.kovospace.newtablinks.environment.dtos.EnvironmentSaveRequestDto;
import com.kovospace.newtablinks.environment.services.EnvironmentService;
import com.kovospace.newtablinks.group.dtos.GroupDto;
import com.kovospace.newtablinks.group.dtos.GroupSaveRequestDto;
import com.kovospace.newtablinks.group.services.GroupService;
import com.kovospace.newtablinks.link.dtos.LinkDto;
import com.kovospace.newtablinks.link.dtos.LinkSaveRequestDto;
import com.kovospace.newtablinks.link.services.LinkService;
import com.kovospace.newtablinks.profile.dtos.ProfileDto;
import com.kovospace.newtablinks.profile.dtos.ProfileSaveRequestDto;
import com.kovospace.newtablinks.profile.services.ProfileService;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The Fair Use Policy caps on the interactive endpoints, against the schema the migration image
 * builds: what is refused, how the refusal looks on the wire, that data already over a cap stays
 * editable, and that two parallel writes at one below a cap cannot both get through.
 *
 * <p>The caps are set small so they are reachable; the sync push has its own test,
 * {@code SyncPushFairUseAgainstMigratedSchemaTest}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class FairUseLimitsAgainstMigratedSchemaTest {

    private static final int PROFILE_CAP = 2;
    private static final int WORKSPACE_CAP = 2;
    private static final int LINKS_PER_WORKSPACE_CAP = 3;

    private static MigratedPostgresDatabase database;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private LinkService linkService;

    @Autowired
    private FairUseLimitGuard fairUseLimitGuard;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
    }

    @Test
    @DisplayName("a profile beyond the cap is refused with 409, code, limit and maximum")
    void shouldAnswerConflictWithTheFairUseBodyForAProfileBeyondTheCap() throws Exception {
        final UserEntity owner = newAccount();
        createProfiles(owner.getId(), PROFILE_CAP);

        mockMvc.perform(post("/api/v1/profiles")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessTokenIssuer.issueAccessTokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"One too many\",\"enableDragAndDrop\":false,"
                                + "\"hideTips\":false,\"dismissedTips\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("FAIR_USE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.limit").value("PROFILES"))
                .andExpect(jsonPath("$.maximum").value(PROFILE_CAP))
                .andExpect(jsonPath("$.validationErrors").isEmpty());

        assertThat(countProfiles(owner.getId())).isEqualTo(PROFILE_CAP);
    }

    @Test
    @DisplayName("an ordinary error body carries no code, limit or maximum at all")
    void shouldLeaveTheNewFieldsOutOfEveryOtherErrorBody() throws Exception {
        final UserEntity owner = newAccount();

        mockMvc.perform(post("/api/v1/profiles")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessTokenIssuer.issueAccessTokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.limit").doesNotExist())
                .andExpect(jsonPath("$.maximum").doesNotExist());
    }

    @Test
    @DisplayName("a workspace beyond the cap is refused, counted across every profile")
    void shouldRefuseAWorkspaceBeyondTheCapAcrossProfiles() {
        final UUID ownerId = newAccount().getId();
        final List<ProfileDto> profiles = createProfiles(ownerId, 2);
        createWorkspace(profiles.get(0).id(), ownerId);
        createWorkspace(profiles.get(1).id(), ownerId);

        assertThatThrownBy(() -> createWorkspace(profiles.get(1).id(), ownerId))
                .isInstanceOfSatisfying(FairUseLimitReachedException.class, refusal -> {
                    assertThat(refusal.getLimit()).isEqualTo(FairUseLimit.WORKSPACES);
                    assertThat(refusal.getMaximum()).isEqualTo(WORKSPACE_CAP);
                });
    }

    @Test
    @DisplayName("a link beyond the cap of its workspace is refused, counted through every group, "
            + "while another workspace still takes links")
    void shouldRefuseALinkBeyondTheCapOfItsWorkspaceOnly() {
        final UUID ownerId = newAccount().getId();
        final UUID profileId = createProfiles(ownerId, 1).getFirst().id();
        final EnvironmentDto fullWorkspace = createWorkspace(profileId, ownerId);
        final GroupDto firstGroup = createGroup(fullWorkspace.id(), ownerId);
        final GroupDto secondGroup = createGroup(fullWorkspace.id(), ownerId);
        createLink(firstGroup.id(), ownerId);
        createLink(firstGroup.id(), ownerId);
        createLink(secondGroup.id(), ownerId);

        assertThatThrownBy(() -> createLink(secondGroup.id(), ownerId))
                .isInstanceOfSatisfying(FairUseLimitReachedException.class, refusal ->
                        assertThat(refusal.getLimit()).isEqualTo(FairUseLimit.LINKS_PER_WORKSPACE));

        final GroupDto groupElsewhere =
                createGroup(createWorkspace(profileId, ownerId).id(), ownerId);
        assertThat(createLink(groupElsewhere.id(), ownerId)).isNotNull();
    }

    @Test
    @DisplayName("data already over a cap is kept, stays editable and deletable, and only "
            + "growth is refused")
    void shouldKeepEditingAndDeletingDataAlreadyOverTheCap() {
        final UUID ownerId = newAccount().getId();
        final UUID profileId = createProfiles(ownerId, 1).getFirst().id();
        final UUID workspaceId = createWorkspace(profileId, ownerId).id();
        final UUID groupId = createGroup(workspaceId, ownerId).id();
        final List<LinkDto> links = List.of(
                createLink(groupId, ownerId), createLink(groupId, ownerId),
                createLink(groupId, ownerId));
        insertLinksBehindTheGuardsBack(groupId, 2);

        final LinkDto edited = linkService.updateLink(links.getFirst().id(),
                new LinkSaveRequestDto(groupId, null, "Renamed", "https://renamed.test", null),
                ownerId);
        assertThat(edited.title()).isEqualTo("Renamed");

        linkService.deleteLink(links.get(1).id(), ownerId);
        assertThat(countLinksInGroup(groupId)).isEqualTo(4);

        assertThatThrownBy(() -> createLink(groupId, ownerId))
                .isInstanceOf(FairUseLimitReachedException.class);
        assertThat(countLinksInGroup(groupId)).isEqualTo(4);
    }

    @Test
    @DisplayName("two parallel writes at one below the cap queue on the account lock, and the "
            + "second sees the first one's row")
    void shouldSerializeTwoParallelWritesAtOneBelowTheCap() throws Exception {
        final UUID ownerId = newAccount().getId();
        createProfiles(ownerId, PROFILE_CAP - 1);

        final CountDownLatch firstHoldsTheLock = new CountDownLatch(1);
        final CountDownLatch releaseTheFirst = new CountDownLatch(1);

        final CompletableFuture<Void> first = CompletableFuture.runAsync(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    fairUseLimitGuard.requireRoomForAnotherProfile(ownerId);
                    insertProfileBehindTheGuardsBack(ownerId);
                    firstHoldsTheLock.countDown();
                    awaitQuietly(releaseTheFirst);
                }));
        assertThat(firstHoldsTheLock.await(10, TimeUnit.SECONDS)).isTrue();

        final CompletableFuture<ProfileDto> second =
                CompletableFuture.supplyAsync(() -> profileService.createProfile(
                        profileRequest("Raced"), ownerId));

        // Still waiting: without the lock it would have counted one profile and gone through.
        Thread.sleep(Duration.ofMillis(500));
        assertThat(second).isNotDone();

        releaseTheFirst.countDown();
        first.get(10, TimeUnit.SECONDS);

        assertThat(second).failsWithin(Duration.ofSeconds(10))
                .withThrowableOfType(java.util.concurrent.ExecutionException.class)
                .withCauseInstanceOf(FairUseLimitReachedException.class);
        assertThat(countProfiles(ownerId)).isEqualTo(PROFILE_CAP);
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Creates an active account.
     *
     * @return the stored account
     */
    private UserEntity newAccount() {
        final String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new UserEntity("fair-" + unique, unique + "@example.com",
                null, "Fair", UserAccountStatus.ACTIVE));
    }

    /**
     * Creates profiles through the guarded service.
     *
     * @param ownerId identifier of the account
     * @param count   how many
     * @return the created profiles, in creation order
     */
    private List<ProfileDto> createProfiles(final UUID ownerId, final int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> profileService.createProfile(
                        profileRequest("Profile " + index), ownerId))
                .toList();
    }

    /**
     * Builds a profile request.
     *
     * @param name the profile's name
     * @return the request
     */
    private static ProfileSaveRequestDto profileRequest(final String name) {
        return new ProfileSaveRequestDto(name, false, false, List.of());
    }

    /**
     * Creates a workspace through the guarded service.
     *
     * @param profileId the profile it is filed under
     * @param ownerId   identifier of the account
     * @return the created workspace
     */
    private EnvironmentDto createWorkspace(final UUID profileId, final UUID ownerId) {
        return environmentService.createEnvironment(
                new EnvironmentSaveRequestDto(profileId, "Workspace", null), ownerId);
    }

    /**
     * Creates a group.
     *
     * @param workspaceId the workspace it belongs to
     * @param ownerId     identifier of the account
     * @return the created group
     */
    private GroupDto createGroup(final UUID workspaceId, final UUID ownerId) {
        return groupService.createGroup(
                new GroupSaveRequestDto(workspaceId, "Group", null), ownerId);
    }

    /**
     * Creates a link through the guarded service.
     *
     * @param groupId the group it sits in
     * @param ownerId identifier of the account
     * @return the created link
     */
    private LinkDto createLink(final UUID groupId, final UUID ownerId) {
        return linkService.createLink(
                new LinkSaveRequestDto(groupId, null, "Link", "https://example.test", null),
                ownerId);
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

    /**
     * Inserts a profile without asking the guard.
     *
     * @param ownerId identifier of the account
     */
    private void insertProfileBehindTheGuardsBack(final UUID ownerId) {
        jdbcTemplate.update("INSERT INTO profile (id, user_id, name, position, created_at, "
                + "updated_at) VALUES (?, ?, 'Racer', 99, now(), now())", UUID.randomUUID(), ownerId);
    }

    /**
     * Counts an account's profiles straight from the database.
     *
     * @param ownerId identifier of the account
     * @return the count
     */
    private int countProfiles(final UUID ownerId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM profile WHERE user_id = ?", Integer.class, ownerId);
    }

    /**
     * Counts a group's links straight from the database.
     *
     * @param groupId identifier of the group
     * @return the count
     */
    private int countLinksInGroup(final UUID groupId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM link WHERE group_id = ?", Integer.class, groupId);
    }

    /**
     * Waits for a latch, giving up after a while so a broken test cannot hang the build.
     *
     * @param latch the latch
     */
    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (final InterruptedException interruption) {
            Thread.currentThread().interrupt();
        }
    }
}
