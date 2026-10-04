package com.kovospace.newtablinks.common.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kovospace.newtablinks.auth.services.AccessTokenIssuer;
import com.kovospace.newtablinks.auth.services.SingleUseCodeService;
import com.kovospace.newtablinks.common.MigratedPostgresDatabase;
import com.kovospace.newtablinks.common.config.ClientRequestHeaders;
import com.kovospace.newtablinks.common.exceptions.FairUseLimitReachedException;
import com.kovospace.newtablinks.common.exceptions.FreePlanLimitReachedException;
import com.kovospace.newtablinks.common.models.FairUseLimit;
import com.kovospace.newtablinks.common.models.FreePlanLimit;
import com.kovospace.newtablinks.entitlement.models.PremiumGrantTerm;
import com.kovospace.newtablinks.entitlement.services.EntitlementGrantService;
import com.kovospace.newtablinks.environment.dtos.EnvironmentDto;
import com.kovospace.newtablinks.environment.dtos.EnvironmentSaveRequestDto;
import com.kovospace.newtablinks.environment.services.EnvironmentService;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The free plan's limits, against the schema the migration image builds: each limit refuses a
 * free account, a premium account is held to the Fair Use caps instead, an account that drops
 * from premium to free keeps and can still edit what it holds, and every way a synchronised
 * installation is recorded - password sign-in and the extension connect code - is refused past
 * the device limit, while the website's own sign-in is never counted.
 *
 * <p>The limits are set small so that they are reachable, and the Fair Use caps just above them
 * so that a premium account can be seen to pass the free limit and stop at the fair one.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class FreePlanLimitsAgainstMigratedSchemaTest {

    private static final int FREE_PROFILES = 1;
    private static final int FREE_WORKSPACES = 2;
    private static final int FREE_DEVICES = 2;
    private static final int FAIR_USE_PROFILES = 3;
    private static final int FAIR_USE_WORKSPACES = 4;
    private static final String PASSWORD = "correct horse battery staple";

    private static MigratedPostgresDatabase database;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private SingleUseCodeService singleUseCodeService;

    @Autowired
    private EntitlementGrantService entitlementGrantService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private EnvironmentService environmentService;

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
        registry.add("newtablinks.plan-limits.free.workspaces", () -> FREE_WORKSPACES);
        registry.add("newtablinks.plan-limits.free.devices", () -> FREE_DEVICES);
        registry.add("newtablinks.fair-use.profiles", () -> FAIR_USE_PROFILES);
        registry.add("newtablinks.fair-use.workspaces", () -> FAIR_USE_WORKSPACES);
    }

    // ------------------------------------------------------------ profiles and workspaces

    @Test
    @DisplayName("a free account's profile beyond the free limit is refused with 409 and the "
            + "free plan's code, although the Fair Use cap is higher")
    void shouldAnswerConflictWithTheFreePlanBodyForAProfileBeyondTheLimit() throws Exception {
        final UserEntity owner = newAccount();
        createProfiles(owner.getId(), FREE_PROFILES);

        mockMvc.perform(post("/api/v1/profiles")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessTokenIssuer.issueAccessTokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Second\",\"enableDragAndDrop\":false,"
                                + "\"hideTips\":false,\"dismissedTips\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("FREE_PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.limit").value("PROFILES"))
                .andExpect(jsonPath("$.maximum").value(FREE_PROFILES))
                .andExpect(jsonPath("$.validationErrors").isEmpty());

        assertThat(countProfiles(owner.getId())).isEqualTo(FREE_PROFILES);
    }

    @Test
    @DisplayName("a free account's workspace beyond the free limit is refused")
    void shouldRefuseAFreeAccountAWorkspaceBeyondTheLimit() {
        final UUID ownerId = newAccount().getId();
        final UUID profileId = createProfiles(ownerId, 1).getFirst().id();
        createWorkspaces(profileId, ownerId, FREE_WORKSPACES);

        assertThatThrownBy(() -> createWorkspace(profileId, ownerId))
                .isInstanceOfSatisfying(FreePlanLimitReachedException.class, refusal -> {
                    assertThat(refusal.getLimit()).isEqualTo(FreePlanLimit.WORKSPACES);
                    assertThat(refusal.getMaximum()).isEqualTo(FREE_WORKSPACES);
                });
    }

    @Test
    @DisplayName("a premium account passes the free limits and stops at the Fair Use caps")
    void shouldHoldAPremiumAccountToTheFairUseCapsInstead() {
        final UserEntity owner = newAccount();
        makePremium(owner);
        final List<ProfileDto> profiles = createProfiles(owner.getId(), FAIR_USE_PROFILES);
        createWorkspaces(profiles.getFirst().id(), owner.getId(), FAIR_USE_WORKSPACES);

        assertThatThrownBy(() -> createProfiles(owner.getId(), 1))
                .isInstanceOfSatisfying(FairUseLimitReachedException.class, refusal ->
                        assertThat(refusal.getLimit()).isEqualTo(FairUseLimit.PROFILES));
        assertThatThrownBy(() -> createWorkspace(profiles.getFirst().id(), owner.getId()))
                .isInstanceOfSatisfying(FairUseLimitReachedException.class, refusal ->
                        assertThat(refusal.getLimit()).isEqualTo(FairUseLimit.WORKSPACES));
    }

    @Test
    @DisplayName("an account that drops from premium to free keeps everything, can still edit "
            + "and delete it, and cannot add")
    void shouldKeepAndEditEverythingAfterADowngrade() {
        final UserEntity owner = newAccount();
        makePremium(owner);
        final List<ProfileDto> profiles = createProfiles(owner.getId(), FAIR_USE_PROFILES);
        final List<EnvironmentDto> workspaces = createWorkspaces(
                profiles.getFirst().id(), owner.getId(), FAIR_USE_WORKSPACES);
        entitlementGrantService.revokeGrantedPro(owner);

        assertThat(countProfiles(owner.getId())).isEqualTo(FAIR_USE_PROFILES);
        profileService.updateProfile(profiles.get(1).id(),
                profileRequest("Renamed after downgrade"), owner.getId());
        environmentService.updateEnvironment(workspaces.get(1).id(), new EnvironmentSaveRequestDto(
                profiles.getFirst().id(), "Renamed after downgrade", null), owner.getId());
        profileService.deleteProfile(profiles.get(2).id(), owner.getId());
        environmentService.deleteEnvironment(workspaces.get(3).id(), owner.getId());

        assertThatThrownBy(() -> createProfiles(owner.getId(), 1))
                .isInstanceOf(FreePlanLimitReachedException.class);
        assertThatThrownBy(() -> createWorkspace(profiles.getFirst().id(), owner.getId()))
                .isInstanceOf(FreePlanLimitReachedException.class);
        assertThat(countProfiles(owner.getId())).isEqualTo(FAIR_USE_PROFILES - 1);
    }

    // ------------------------------------------------------------ sync push

    @Test
    @DisplayName("a push growing a free account past the free limit is refused whole; after a "
            + "downgrade a push editing data over the limit goes through")
    void shouldJudgeTheSyncPushOfAFreeAccountAgainstTheFreeLimits() {
        final UserEntity owner = newAccount();
        final UUID profileId = UUID.randomUUID();
        push(owner, upsertProfile(profileId, "Only"));

        assertThatThrownBy(() -> push(owner,
                upsertProfile(profileId, "Edited"), upsertProfile(UUID.randomUUID(), "Second")))
                .isInstanceOfSatisfying(FreePlanLimitReachedException.class, refusal ->
                        assertThat(refusal.getLimit()).isEqualTo(FreePlanLimit.PROFILES));
        assertThat(profileName(profileId)).isEqualTo("Only");

        final UUID[] workspaces = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        assertThatThrownBy(() -> push(owner, upsertWorkspace(workspaces[0], profileId),
                upsertWorkspace(workspaces[1], profileId), upsertWorkspace(workspaces[2], profileId)))
                .isInstanceOfSatisfying(FreePlanLimitReachedException.class, refusal ->
                        assertThat(refusal.getLimit()).isEqualTo(FreePlanLimit.WORKSPACES));

        makePremium(owner);
        final UUID secondProfileId = UUID.randomUUID();
        push(owner, upsertProfile(secondProfileId, "Second"));
        entitlementGrantService.revokeGrantedPro(owner);

        push(owner, upsertProfile(secondProfileId, "Edited while free"));
        assertThat(profileName(secondProfileId)).isEqualTo("Edited while free");
        push(owner, upsertProfile(UUID.randomUUID(), "Replacement"),
                delete(SyncEntityKind.PROFILE, secondProfileId));
        assertThat(countProfiles(owner.getId())).isEqualTo(2);
    }

    // ------------------------------------------------------------ devices

    @Test
    @DisplayName("password sign-in from a new installation beyond the free limit is refused with "
            + "409 DEVICES; known installations and the website keep signing in")
    void shouldRefuseANewInstallationBeyondTheFreeLimitOnPasswordSignIn() throws Exception {
        final UserEntity owner = newAccountWithPassword();
        final List<UUID> installations = IntStream.range(0, FREE_DEVICES)
                .mapToObj(index -> UUID.randomUUID()).toList();
        for (final UUID installation : installations) {
            signIn(owner, installation).andExpect(status().isOk());
        }

        signIn(owner, UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("FREE_PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.limit").value("DEVICES"))
                .andExpect(jsonPath("$.maximum").value(FREE_DEVICES))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        signIn(owner, installations.getFirst()).andExpect(status().isOk());
        signIn(owner, null).andExpect(status().isOk());
        assertThat(countInstallations(owner.getId())).isEqualTo(FREE_DEVICES);
        assertThat(countLiveTokensOfAccount(owner.getId())).isEqualTo(FREE_DEVICES + 2);
    }

    @Test
    @DisplayName("redeeming a connect code from a new installation beyond the free limit is "
            + "refused, and the code is not spent by the refusal")
    void shouldRefuseANewInstallationBeyondTheFreeLimitOnTheConnectCode() throws Exception {
        final UserEntity owner = newAccount();
        for (int index = 0; index < FREE_DEVICES; index++) {
            redeemConnectCode(mintConnectCode(owner), UUID.randomUUID())
                    .andExpect(status().isOk());
        }
        final String code = mintConnectCode(owner);

        redeemConnectCode(code, UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FREE_PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.limit").value("DEVICES"));

        forgetOneInstallation(owner.getId());
        redeemConnectCode(code, UUID.randomUUID()).andExpect(status().isOk());
        assertThat(countInstallations(owner.getId())).isEqualTo(FREE_DEVICES);
    }

    @Test
    @DisplayName("a premium account has no device limit; after a downgrade its installations "
            + "keep signing in and only a new one is refused")
    void shouldLeavePremiumUnlimitedAndKeepInstallationsAfterADowngrade() throws Exception {
        final UserEntity owner = newAccountWithPassword();
        makePremium(owner);
        final List<UUID> installations = IntStream.range(0, FREE_DEVICES + 2)
                .mapToObj(index -> UUID.randomUUID()).toList();
        for (final UUID installation : installations) {
            signIn(owner, installation).andExpect(status().isOk());
        }
        entitlementGrantService.revokeGrantedPro(owner);

        for (final UUID installation : installations) {
            signIn(owner, installation).andExpect(status().isOk());
        }
        signIn(owner, UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.limit").value("DEVICES"));
        assertThat(countInstallations(owner.getId())).isEqualTo(FREE_DEVICES + 2);
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Creates an active account without a password.
     *
     * @return the stored account
     */
    private UserEntity newAccount() {
        final String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new UserEntity("free-" + unique, unique + "@example.com",
                null, "Free", UserAccountStatus.ACTIVE));
    }

    /**
     * Creates an active account that can sign in with {@link #PASSWORD}.
     *
     * @return the stored account
     */
    private UserEntity newAccountWithPassword() {
        final String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new UserEntity("free-" + unique, unique + "@example.com",
                passwordEncoder.encode(PASSWORD), "Free", UserAccountStatus.ACTIVE));
    }

    /**
     * Makes an account premium through an operator grant.
     *
     * @param owner the account
     */
    private void makePremium(final UserEntity owner) {
        entitlementGrantService.grantPro(owner, PremiumGrantTerm.LIFETIME);
    }

    /**
     * Signs in with the password, as the extension or, without an installation, the website.
     *
     * @param owner        the account
     * @param installation the installation reporting itself, {@code null} for the website
     * @return the result
     * @throws Exception when the request cannot be performed
     */
    private ResultActions signIn(final UserEntity owner, final UUID installation)
            throws Exception {

        final var request = post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"" + owner.getUsername()
                        + "\",\"password\":\"" + PASSWORD + "\"}");
        if (installation != null) {
            request.header(ClientRequestHeaders.INSTALLATION_ID, installation.toString());
        }
        return mockMvc.perform(request);
    }

    /**
     * Mints a connect code, as the website does for a signed-in user.
     *
     * @param owner the account
     * @return the code
     */
    private String mintConnectCode(final UserEntity owner) {
        return singleUseCodeService.mintExtensionConnectCode(owner).code();
    }

    /**
     * Redeems a connect code, as the extension does.
     *
     * @param code         the code
     * @param installation the installation redeeming it
     * @return the result
     * @throws Exception when the request cannot be performed
     */
    private ResultActions redeemConnectCode(final String code, final UUID installation)
            throws Exception {

        return mockMvc.perform(post("/api/v1/auth/extension-connect")
                .header(ClientRequestHeaders.INSTALLATION_ID, installation.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    /**
     * Forgets one of an account's installations, as the website's device list does.
     *
     * @param ownerId identifier of the account
     */
    private void forgetOneInstallation(final UUID ownerId) {
        final UUID deviceId = jdbcTemplate.queryForObject("SELECT id FROM user_device "
                + "WHERE user_id = ? AND installation_id IS NOT NULL LIMIT 1", UUID.class, ownerId);
        jdbcTemplate.update("DELETE FROM refresh_token WHERE device_id = ?", deviceId);
        jdbcTemplate.update("DELETE FROM user_device WHERE id = ?", deviceId);
    }

    /**
     * Creates profiles through the guarded service.
     *
     * @param ownerId identifier of the account
     * @param count   how many
     * @return the created profiles, in creation order
     */
    private List<ProfileDto> createProfiles(final UUID ownerId, final int count) {
        return IntStream.range(0, count)
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
     * Creates workspaces through the guarded service.
     *
     * @param profileId the profile they are filed under
     * @param ownerId   identifier of the account
     * @param count     how many
     * @return the created workspaces, in creation order
     */
    private List<EnvironmentDto> createWorkspaces(
            final UUID profileId, final UUID ownerId, final int count) {

        return IntStream.range(0, count)
                .mapToObj(index -> createWorkspace(profileId, ownerId))
                .toList();
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
     * Pushes a batch as the account.
     *
     * @param owner      the account
     * @param operations the batch
     */
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

    private int countInstallations(final UUID ownerId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM user_device "
                + "WHERE user_id = ? AND installation_id IS NOT NULL", Integer.class, ownerId);
    }

    private int countLiveTokensOfAccount(final UUID ownerId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_token "
                + "WHERE user_id = ? AND revoked_at IS NULL", Integer.class, ownerId);
    }
}
