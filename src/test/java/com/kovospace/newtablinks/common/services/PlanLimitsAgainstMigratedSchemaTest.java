package com.kovospace.newtablinks.common.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kovospace.newtablinks.auth.services.AccessTokenIssuer;
import com.kovospace.newtablinks.common.MigratedPostgresDatabase;
import com.kovospace.newtablinks.common.exceptions.PlanLimitReachedException;
import com.kovospace.newtablinks.common.models.PlanLimit;
import com.kovospace.newtablinks.common.models.PlanLimitRefusalCode;
import com.kovospace.newtablinks.entitlement.models.PremiumGrantTerm;
import com.kovospace.newtablinks.entitlement.services.EntitlementGrantService;
import com.kovospace.newtablinks.environment.dtos.EnvironmentDto;
import com.kovospace.newtablinks.environment.dtos.EnvironmentSaveRequestDto;
import com.kovospace.newtablinks.environment.services.EnvironmentService;
import com.kovospace.newtablinks.group.dtos.GroupDto;
import com.kovospace.newtablinks.group.dtos.GroupSaveRequestDto;
import com.kovospace.newtablinks.group.services.GroupService;
import com.kovospace.newtablinks.link.dtos.LinkSaveRequestDto;
import com.kovospace.newtablinks.link.services.LinkService;
import com.kovospace.newtablinks.profile.dtos.ProfileDto;
import com.kovospace.newtablinks.profile.dtos.ProfileSaveRequestDto;
import com.kovospace.newtablinks.profile.services.ProfileService;
import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.dtos.SyncOperationKind;
import com.kovospace.newtablinks.sync.dtos.SyncPushRequestDto;
import com.kovospace.newtablinks.sync.services.SyncPushService;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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

/**
 * The plan limits as slots, against the schema the migration image builds: the free plan's
 * lower limits and their refusal on the wire, workspaces counted per profile, a premium account
 * held to the Fair Use Policy instead, writes into a profile or workspace without a slot refused
 * - on the REST endpoints and in the sync push alike - while that data is kept and can be
 * deleted, the closed-tab history trimmed gently after a downgrade, and the effective limits
 * as the extension reads them.
 *
 * <p>The limits are set small so that they are reachable; the Fair Use caps sit just above the
 * free ones so that a premium account can be seen to pass the free limit and stop at the fair
 * one. Installations and the inventory report have their own test.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlanLimitsAgainstMigratedSchemaTest {

    private static final int FREE_PROFILES = 1;
    private static final int FREE_WORKSPACES_PER_PROFILE = 2;
    private static final int FREE_CLOSED_TABS = 2;
    private static final int PREMIUM_PROFILES = 3;
    private static final int PREMIUM_WORKSPACES_PER_PROFILE = 3;
    private static final int PREMIUM_CLOSED_TABS = 5;
    private static final String WEBSITE = "https://tabilinks.example";

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
    private EntitlementGrantService entitlementGrantService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private LinkService linkService;

    @Autowired
    private SyncPushService syncPushService;

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
     * Points the application at the migrated database, with limits small enough to reach.
     *
     * @param registry the property registry
     */
    @DynamicPropertySource
    static void useTheMigratedDatabaseAndSmallLimits(final DynamicPropertyRegistry registry) {
        database.registerDataSource(registry);
        registry.add("newtablinks.plan-limits.free.profiles", () -> FREE_PROFILES);
        registry.add("newtablinks.plan-limits.free.workspaces-per-profile",
                () -> FREE_WORKSPACES_PER_PROFILE);
        registry.add("newtablinks.plan-limits.free.closed-tabs", () -> FREE_CLOSED_TABS);
        registry.add("newtablinks.plan-limits.premium.profiles", () -> PREMIUM_PROFILES);
        registry.add("newtablinks.plan-limits.premium.workspaces-per-profile",
                () -> PREMIUM_WORKSPACES_PER_PROFILE);
        registry.add("newtablinks.plan-limits.premium.closed-tabs", () -> PREMIUM_CLOSED_TABS);
        registry.add("newtablinks.web.base-url", () -> WEBSITE);
    }

    // ------------------------------------------------------------ slots on the REST endpoints

    @Test
    @DisplayName("a free account's profile beyond the free slots is refused with 409, the free "
            + "plan's code, the limit, the maximum and the devices page")
    void shouldAnswerConflictWithTheFreePlanBodyForAProfileBeyondTheSlots() throws Exception {
        final UserEntity owner = newAccount();
        createProfiles(owner.getId(), FREE_PROFILES);

        mockMvc.perform(post("/api/v1/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Second\",\"enableDragAndDrop\":false,"
                                + "\"hideTips\":false,\"dismissedTips\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("FREE_PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.limit").value("PROFILES"))
                .andExpect(jsonPath("$.maximum").value(FREE_PROFILES))
                .andExpect(jsonPath("$.manageUrl").value(WEBSITE + "/devices"));

        assertThat(countProfiles(owner.getId())).isEqualTo(FREE_PROFILES);
    }

    @Test
    @DisplayName("a free account's workspaces are counted per profile, and a premium account "
            + "passes the free limits to stop at the Fair Use caps")
    void shouldCountWorkspacesPerProfileAndHoldPremiumToTheFairUseCaps() {
        final UserEntity owner = newAccount();
        final UUID profileId = createProfiles(owner.getId(), 1).getFirst().id();
        createWorkspaces(profileId, owner.getId(), FREE_WORKSPACES_PER_PROFILE);

        assertRefused(() -> createWorkspace(profileId, owner.getId()),
                PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED, PlanLimit.WORKSPACES_PER_PROFILE);

        makePremium(owner);
        createWorkspace(profileId, owner.getId());
        final List<ProfileDto> moreProfiles = createProfiles(owner.getId(), PREMIUM_PROFILES - 1);
        createWorkspaces(moreProfiles.getFirst().id(), owner.getId(),
                PREMIUM_WORKSPACES_PER_PROFILE);

        assertRefused(() -> createProfiles(owner.getId(), 1),
                PlanLimitRefusalCode.FAIR_USE_LIMIT_REACHED, PlanLimit.PROFILES);
        assertRefused(() -> createWorkspace(profileId, owner.getId()),
                PlanLimitRefusalCode.FAIR_USE_LIMIT_REACHED, PlanLimit.WORKSPACES_PER_PROFILE);
    }

    @Test
    @DisplayName("after premium ends, the first profile and its first workspaces keep "
            + "synchronising; writes into anything without a slot are refused, deleting it is "
            + "not, and upgrading again releases it")
    void shouldRefuseWritesIntoProfilesAndWorkspacesWithoutSlotAfterADowngrade() {
        final UserEntity owner = newAccount();
        makePremium(owner);
        final List<ProfileDto> profiles = createProfiles(owner.getId(), PREMIUM_PROFILES);
        final UUID firstProfileId = profiles.getFirst().id();
        final List<EnvironmentDto> workspaces = createWorkspaces(
                firstProfileId, owner.getId(), PREMIUM_WORKSPACES_PER_PROFILE);
        final GroupDto groupWithoutSlot = createGroup(workspaces.getLast().id(), owner.getId());
        entitlementGrantService.revokeGrantedPro(owner);

        profileService.updateProfile(firstProfileId, profileRequest("Still mine"), owner.getId());
        environmentService.updateEnvironment(workspaces.get(1).id(),
                new EnvironmentSaveRequestDto(firstProfileId, "Still mine", null), owner.getId());
        createGroup(workspaces.getFirst().id(), owner.getId());

        assertRefused(() -> profileService.updateProfile(profiles.get(1).id(),
                        profileRequest("Edited"), owner.getId()),
                PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED, PlanLimit.PROFILES);
        assertRefused(() -> environmentService.updateEnvironment(workspaces.getLast().id(),
                        new EnvironmentSaveRequestDto(firstProfileId, "Edited", null),
                        owner.getId()),
                PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED, PlanLimit.WORKSPACES_PER_PROFILE);
        assertRefused(() -> createGroup(workspaces.getLast().id(), owner.getId()),
                PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED, PlanLimit.WORKSPACES_PER_PROFILE);
        assertRefused(() -> createLink(groupWithoutSlot.id(), owner.getId()),
                PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED, PlanLimit.WORKSPACES_PER_PROFILE);
        assertRefused(() -> groupService.deleteGroup(groupWithoutSlot.id(), owner.getId()),
                PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED, PlanLimit.WORKSPACES_PER_PROFILE);

        environmentService.deleteEnvironment(workspaces.getLast().id(), owner.getId());
        profileService.deleteProfile(profiles.getLast().id(), owner.getId());
        assertThat(countProfiles(owner.getId())).isEqualTo(PREMIUM_PROFILES - 1);

        makePremium(owner);
        profileService.updateProfile(profiles.get(1).id(), profileRequest("Back"), owner.getId());
    }

    @Test
    @DisplayName("slots go by the order the server first stored each record: deleting a slot "
            + "holder hands its slot to the next one")
    void shouldHandTheSlotOfADeletedWorkspaceToTheNextOne() {
        final UserEntity owner = newAccount();
        makePremium(owner);
        final UUID profileId = createProfiles(owner.getId(), 1).getFirst().id();
        final List<EnvironmentDto> workspaces = createWorkspaces(
                profileId, owner.getId(), FREE_WORKSPACES_PER_PROFILE + 1);
        entitlementGrantService.revokeGrantedPro(owner);
        final EnvironmentSaveRequestDto rename =
                new EnvironmentSaveRequestDto(profileId, "Renamed", null);

        assertRefused(() -> environmentService.updateEnvironment(
                        workspaces.getLast().id(), rename, owner.getId()),
                PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED, PlanLimit.WORKSPACES_PER_PROFILE);

        environmentService.deleteEnvironment(workspaces.getFirst().id(), owner.getId());
        assertThat(environmentService.updateEnvironment(
                workspaces.getLast().id(), rename, owner.getId()).name()).isEqualTo("Renamed");
    }

    // ------------------------------------------------------------------- slots in the push

    @Test
    @DisplayName("a push creating beyond a free account's slots is refused whole")
    void shouldRefuseAPushCreatingBeyondTheFreeSlots() {
        final UserEntity owner = newAccount();
        final UUID profileId = UUID.randomUUID();
        push(owner, upsertProfile(profileId, "Only"));

        assertRefused(() -> push(owner,
                        upsertProfile(profileId, "Edited"), upsertProfile(UUID.randomUUID(), "2nd")),
                PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED, PlanLimit.PROFILES);
        assertThat(profileName(profileId)).isEqualTo("Only");

        assertRefused(() -> push(owner,
                        upsertWorkspace(UUID.randomUUID(), profileId),
                        upsertWorkspace(UUID.randomUUID(), profileId),
                        upsertWorkspace(UUID.randomUUID(), profileId)),
                PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED, PlanLimit.WORKSPACES_PER_PROFILE);
        assertThat(countWorkspaces(owner.getId())).isZero();
    }

    @Test
    @DisplayName("after a downgrade a push writing into a profile or workspace without a slot is "
            + "refused; one writing into slot holders, or deleting what holds none, goes through")
    void shouldJudgeThePushOfADowngradedAccountBySlots() {
        final UserEntity owner = newAccount();
        makePremium(owner);
        final UUID firstProfile = UUID.randomUUID();
        final UUID secondProfile = UUID.randomUUID();
        final UUID[] workspaces = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        final UUID groupWithoutSlot = UUID.randomUUID();
        push(owner, upsertProfile(firstProfile, "First"));
        push(owner, upsertProfile(secondProfile, "Second"));
        push(owner, upsertWorkspace(workspaces[0], firstProfile));
        push(owner, upsertWorkspace(workspaces[1], firstProfile));
        push(owner, upsertWorkspace(workspaces[2], firstProfile),
                upsertGroup(groupWithoutSlot, workspaces[2]));
        entitlementGrantService.revokeGrantedPro(owner);

        push(owner, upsertProfile(firstProfile, "Edited"),
                upsertGroup(UUID.randomUUID(), workspaces[1]));

        assertRefused(() -> push(owner, upsertProfile(secondProfile, "Edited")),
                PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED, PlanLimit.PROFILES);
        assertRefused(() -> push(owner, upsertLink(UUID.randomUUID(), groupWithoutSlot)),
                PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED, PlanLimit.WORKSPACES_PER_PROFILE);
        assertRefused(() -> push(owner, delete(SyncEntityKind.GROUP, groupWithoutSlot)),
                PlanLimitRefusalCode.FREE_PLAN_LIMIT_REACHED, PlanLimit.WORKSPACES_PER_PROFILE);

        push(owner, delete(SyncEntityKind.ENVIRONMENT, workspaces[2]),
                delete(SyncEntityKind.PROFILE, secondProfile));
        assertThat(countProfiles(owner.getId())).isEqualTo(1);
        assertThat(countWorkspaces(owner.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("a push replacing the only profile slot - a new profile and the old one's "
            + "deletion in one batch - goes through, judged on its end state")
    void shouldAdmitAPushReplacingTheOnlyProfile() {
        final UserEntity owner = newAccount();
        final UUID original = UUID.randomUUID();
        push(owner, upsertProfile(original, "Original"));
        final UUID replacement = UUID.randomUUID();

        push(owner, upsertProfile(replacement, "Replacement"),
                delete(SyncEntityKind.PROFILE, original));

        assertThat(profileName(replacement)).isEqualTo("Replacement");
        assertThat(countProfiles(owner.getId())).isEqualTo(1);
    }

    // --------------------------------------------------------------------------- closed tabs

    @Test
    @DisplayName("closed tabs are trimmed to the free limit, and after a downgrade a longer "
            + "history rolls at its size instead of shrinking at once")
    void shouldTrimClosedTabsToThePlanWithoutCuttingAHistoryAtOnce() {
        final UserEntity freeOwner = newAccount();
        final UUID freeProfile = UUID.randomUUID();
        push(freeOwner, upsertProfile(freeProfile, "Free"),
                upsertClosedTab(UUID.randomUUID(), freeProfile, 1),
                upsertClosedTab(UUID.randomUUID(), freeProfile, 2),
                upsertClosedTab(UUID.randomUUID(), freeProfile, 3));
        assertThat(countClosedTabs(freeOwner.getId())).isEqualTo(FREE_CLOSED_TABS);

        final UserEntity downgraded = newAccount();
        makePremium(downgraded);
        final UUID profile = UUID.randomUUID();
        final UUID oldest = UUID.randomUUID();
        push(downgraded, upsertProfile(profile, "Was premium"),
                upsertClosedTab(oldest, profile, 1),
                upsertClosedTab(UUID.randomUUID(), profile, 2),
                upsertClosedTab(UUID.randomUUID(), profile, 3),
                upsertClosedTab(UUID.randomUUID(), profile, 4));
        entitlementGrantService.revokeGrantedPro(downgraded);

        push(downgraded, upsertClosedTab(UUID.randomUUID(), profile, 5));

        assertThat(countClosedTabs(downgraded.getId())).isEqualTo(4);
        assertThat(closedTabExists(oldest)).isFalse();
    }

    // ------------------------------------------------------------------- effective limits

    @Test
    @DisplayName("the effective limits follow the account's standing, on their own endpoint and "
            + "on the sync snapshot")
    void shouldTellTheExtensionWhichLimitsHold() throws Exception {
        final UserEntity owner = newAccount();

        mockMvc.perform(get("/api/v1/users/me/plan-limits")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(false))
                .andExpect(jsonPath("$.limits.profiles").value(FREE_PROFILES))
                .andExpect(jsonPath("$.limits.workspacesPerProfile")
                        .value(FREE_WORKSPACES_PER_PROFILE))
                .andExpect(jsonPath("$.limits.groupsPerWorkspace").value(25))
                .andExpect(jsonPath("$.limits.subgroupsPerGroup").value(25))
                .andExpect(jsonPath("$.limits.linksPerWorkspace").value(500))
                .andExpect(jsonPath("$.limits.closedTabs").value(FREE_CLOSED_TABS))
                .andExpect(jsonPath("$.limits.devices").value(5));

        makePremium(owner);
        mockMvc.perform(get("/api/v1/sync/snapshot")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planLimits.premium").value(true))
                .andExpect(jsonPath("$.planLimits.limits.profiles").value(PREMIUM_PROFILES))
                .andExpect(jsonPath("$.planLimits.limits.closedTabs").value(PREMIUM_CLOSED_TABS))
                .andExpect(jsonPath("$.planLimits.limits.devices").value(100));
    }

    // ------------------------------------------------------------------------- fixtures

    /**
     * Asserts that a call is refused with the given code and limit.
     *
     * @param call  the call
     * @param code  the expected code
     * @param limit the expected limit
     */
    private static void assertRefused(
            final ThrowingCallable call,
            final PlanLimitRefusalCode code,
            final PlanLimit limit) {

        final Consumer<PlanLimitReachedException> matchesCodeAndLimit = refusal -> {
            assertThat(refusal.getCode()).isEqualTo(code);
            assertThat(refusal.getLimit()).isEqualTo(limit);
        };
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(PlanLimitReachedException.class, matchesCodeAndLimit);
    }

    private UserEntity newAccount() {
        final String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new UserEntity("plan-" + unique, unique + "@example.com",
                null, "Plan", UserAccountStatus.ACTIVE));
    }

    private void makePremium(final UserEntity owner) {
        entitlementGrantService.grantPro(owner, PremiumGrantTerm.LIFETIME);
    }

    private String bearer(final UserEntity owner) {
        return "Bearer " + accessTokenIssuer.issueAccessTokenFor(owner);
    }

    private List<ProfileDto> createProfiles(final UUID ownerId, final int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> profileService.createProfile(
                        profileRequest("Profile " + index), ownerId))
                .toList();
    }

    private static ProfileSaveRequestDto profileRequest(final String name) {
        return new ProfileSaveRequestDto(name, false, false, List.of());
    }

    private List<EnvironmentDto> createWorkspaces(
            final UUID profileId, final UUID ownerId, final int count) {

        return IntStream.range(0, count)
                .mapToObj(index -> createWorkspace(profileId, ownerId))
                .toList();
    }

    private EnvironmentDto createWorkspace(final UUID profileId, final UUID ownerId) {
        return environmentService.createEnvironment(
                new EnvironmentSaveRequestDto(profileId, "Workspace", null), ownerId);
    }

    private GroupDto createGroup(final UUID workspaceId, final UUID ownerId) {
        return groupService.createGroup(
                new GroupSaveRequestDto(workspaceId, "Group", null), ownerId);
    }

    private void createLink(final UUID groupId, final UUID ownerId) {
        linkService.createLink(
                new LinkSaveRequestDto(groupId, null, "Link", "https://link.test", null), ownerId);
    }

    private void push(final UserEntity owner, final SyncOperationDto... operations) {
        assertThat(syncPushService.applyPushedOperations(
                new SyncPushRequestDto("a-device", List.of(operations)), owner.getId())
                .rejected()).isEmpty();
    }

    private static SyncOperationDto upsertProfile(final UUID id, final String name) {
        return new SyncOperationDto(SyncOperationKind.UPSERT, SyncEntityKind.PROFILE, id,
                null, null, null, null, name, null, null, null, null,
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

    private static SyncOperationDto upsertLink(final UUID id, final UUID groupId) {
        return new SyncOperationDto(SyncOperationKind.UPSERT, SyncEntityKind.LINK, id,
                null, null, groupId, null, null, null, "Link", "https://link.test", null,
                null, null, null, null, null, null, null, null, null, 0);
    }

    private static SyncOperationDto upsertClosedTab(
            final UUID id, final UUID profileId, final int minutesAfterTen) {

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

    private String profileName(final UUID profileId) {
        return jdbcTemplate.queryForObject(
                "SELECT name FROM profile WHERE id = ?", String.class, profileId);
    }

    private int countProfiles(final UUID ownerId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM profile WHERE user_id = ?", Integer.class, ownerId);
    }

    private int countWorkspaces(final UUID ownerId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM environment WHERE user_id = ?", Integer.class, ownerId);
    }

    private int countClosedTabs(final UUID ownerId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM closed_tab tab "
                + "JOIN profile p ON p.id = tab.profile_id WHERE p.user_id = ?",
                Integer.class, ownerId);
    }

    private boolean closedTabExists(final UUID closedTabId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM closed_tab WHERE id = ?", Integer.class, closedTabId) > 0;
    }
}
