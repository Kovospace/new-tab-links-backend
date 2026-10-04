package com.kovospace.newtablinks.user.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kovospace.newtablinks.auth.services.AccessTokenIssuer;
import com.kovospace.newtablinks.auth.services.SingleUseCodeService;
import com.kovospace.newtablinks.common.MigratedPostgresDatabase;
import com.kovospace.newtablinks.common.config.ClientRequestHeaders;
import com.kovospace.newtablinks.entitlement.models.PremiumGrantTerm;
import com.kovospace.newtablinks.entitlement.services.EntitlementGrantService;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Signed-in installations and the inventory report, against the schema the migration image
 * builds: installations are counted while signed in, never as device rows; a sign-in past the
 * limit is refused on every path that records an installation; the installations beyond the free
 * limit after a downgrade are signed out at their next refresh, in the order they first signed
 * in; and each installation's inventory is stored as reported, summarised on the device list and
 * readable per device by its owner only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InstallationLimitAndInventoryAgainstMigratedSchemaTest {

    private static final int FREE_DEVICES = 2;
    private static final String PASSWORD = "correct horse battery staple";
    private static final String WEBSITE = "https://tabilinks.example";

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
     * Points the application at the migrated database, with a device limit small enough to reach.
     *
     * @param registry the property registry
     */
    @DynamicPropertySource
    static void useTheMigratedDatabaseAndASmallDeviceLimit(final DynamicPropertyRegistry registry) {
        database.registerDataSource(registry);
        registry.add("newtablinks.plan-limits.free.devices", () -> FREE_DEVICES);
        registry.add("newtablinks.web.base-url", () -> WEBSITE);
    }

    // --------------------------------------------------------------------- installations

    @Test
    @DisplayName("a sign-in past the signed-in installations is refused with 409 DEVICES and the "
            + "devices page - also when it would claim the website's signed-in row; one already "
            + "signed in and the website still sign in, and signing one out makes room although "
            + "its row stays")
    void shouldCountSignedInInstallationsNotDeviceRows() throws Exception {
        final UserEntity owner = newAccountWithPassword();
        final List<UUID> installations = List.of(UUID.randomUUID(), UUID.randomUUID());
        final List<String> deviceIds = new ArrayList<>();
        for (final UUID installation : installations) {
            deviceIds.add(deviceIdOf(signIn(owner, installation)
                    .andExpect(status().isOk()).andReturn()));
        }

        signIn(owner, UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FREE_PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.limit").value("DEVICES"))
                .andExpect(jsonPath("$.maximum").value(FREE_DEVICES))
                .andExpect(jsonPath("$.manageUrl").value(WEBSITE + "/devices"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        signIn(owner, installations.getFirst()).andExpect(status().isOk());
        signIn(owner, null).andExpect(status().isOk());

        signIn(owner, UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.limit").value("DEVICES"));

        signOutDevice(owner, deviceIds.getLast());
        signIn(owner, UUID.randomUUID()).andExpect(status().isOk());
        assertThat(countInstallationRows(owner.getId())).isEqualTo(FREE_DEVICES + 1);
    }

    @Test
    @DisplayName("redeeming a connect code past the limit is refused, and the refusal does not "
            + "spend the code")
    void shouldRefuseTheConnectCodePastTheLimitWithoutSpendingIt() throws Exception {
        final UserEntity owner = newAccountWithPassword();
        final List<String> deviceIds = new ArrayList<>();
        for (int index = 0; index < FREE_DEVICES; index++) {
            deviceIds.add(deviceIdOf(redeemConnectCode(mintConnectCode(owner),
                    UUID.randomUUID()).andExpect(status().isOk()).andReturn()));
        }
        final String code = mintConnectCode(owner);

        redeemConnectCode(code, UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.limit").value("DEVICES"));

        signOutDevice(owner, deviceIds.getFirst());
        redeemConnectCode(code, UUID.randomUUID()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("when premium ends, the installations beyond the free limit - in the order they "
            + "first signed in - are signed out at their next refresh, and the first keep working")
    void shouldSignOutTheInstallationsBeyondTheLimitAtTheirNextRefresh() throws Exception {
        final UserEntity owner = newAccountWithPassword();
        entitlementGrantService.grantPro(owner, PremiumGrantTerm.LIFETIME);
        final List<String> refreshTokens = new ArrayList<>();
        for (int index = 0; index < FREE_DEVICES + 2; index++) {
            refreshTokens.add(refreshTokenOf(signIn(owner, UUID.randomUUID())
                    .andExpect(status().isOk()).andReturn()));
        }
        entitlementGrantService.revokeGrantedPro(owner);

        refresh(refreshTokens.get(3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FREE_PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.limit").value("DEVICES"))
                .andExpect(jsonPath("$.maximum").value(FREE_DEVICES));
        refresh(refreshTokens.get(3)).andExpect(status().isUnauthorized());

        refresh(refreshTokens.get(0)).andExpect(status().isOk());
        refresh(refreshTokens.get(1)).andExpect(status().isOk());
        refresh(refreshTokens.get(2)).andExpect(status().isConflict());
        assertThat(countLiveTokensOfInstallations(owner.getId())).isEqualTo(FREE_DEVICES);
    }

    // ------------------------------------------------------------------------- inventory

    @Test
    @DisplayName("an installation's inventory is stored as reported, summarised on the device "
            + "list, readable by its owner only, and replaced by the next report")
    void shouldStoreSummariseAndServeTheInventory() throws Exception {
        final UserEntity owner = newAccountWithPassword();
        final UUID installation = UUID.randomUUID();
        final String deviceId = JsonPath.read(signIn(owner, installation)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.deviceId");
        final UUID profileInAccount = UUID.randomUUID();

        reportInventory(owner, installation, """
                {"profiles":[
                  {"accountId":"%s","name":"Default","syncState":"SYNCHRONISED","workspaces":[
                    {"accountId":null,"name":"Work","syncState":"SYNCHRONISED",
                     "groupCount":4,"subgroupCount":2,"linkCount":37},
                    {"accountId":null,"name":"Hobby","syncState":"LOCAL_ONLY_FREE_LIMIT",
                     "groupCount":1,"subgroupCount":0,"linkCount":3}]},
                  {"accountId":null,"name":"Example","syncState":"EXAMPLE","workspaces":[]}]}
                """.formatted(profileInAccount))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me/devices").header(HttpHeaders.AUTHORIZATION,
                        bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + deviceId + "')].syncSummary")
                        .value("PARTIAL"))
                .andExpect(jsonPath("$[?(@.id == '" + deviceId + "')].inventoryReportedAt")
                        .isNotEmpty());

        mockMvc.perform(get("/api/v1/users/me/devices/" + deviceId + "/inventory")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportedAt").isNotEmpty())
                .andExpect(jsonPath("$.profiles[0].accountId").value(profileInAccount.toString()))
                .andExpect(jsonPath("$.profiles[0].workspaces[1].name").value("Hobby"))
                .andExpect(jsonPath("$.profiles[0].workspaces[1].syncState")
                        .value("LOCAL_ONLY_FREE_LIMIT"))
                .andExpect(jsonPath("$.profiles[0].workspaces[0].linkCount").value(37))
                .andExpect(jsonPath("$.profiles[1].syncState").value("EXAMPLE"));

        mockMvc.perform(get("/api/v1/users/me/devices/" + deviceId + "/inventory")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAccountWithPassword())))
                .andExpect(status().isNotFound());

        reportInventory(owner, installation, """
                {"profiles":[{"accountId":null,"name":"Default",
                  "syncState":"LOCAL_ONLY_FREE_LIMIT","workspaces":[]}]}
                """).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users/me/devices").header(HttpHeaders.AUTHORIZATION,
                        bearer(owner)))
                .andExpect(jsonPath("$[?(@.id == '" + deviceId + "')].syncSummary")
                        .value("NOT_SYNCHRONISED"));
    }

    @Test
    @DisplayName("a device that never reported is UNKNOWN with no detail; a report without an "
            + "installation is 404, and a malformed or oversized one 400")
    void shouldRefuseReportsItCannotPlaceOrBound() throws Exception {
        final UserEntity owner = newAccountWithPassword();
        final UUID installation = UUID.randomUUID();
        final String deviceId = JsonPath.read(signIn(owner, installation)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.deviceId");

        mockMvc.perform(get("/api/v1/users/me/devices").header(HttpHeaders.AUTHORIZATION,
                        bearer(owner)))
                .andExpect(jsonPath("$[?(@.id == '" + deviceId + "')].syncSummary")
                        .value("UNKNOWN"));
        mockMvc.perform(get("/api/v1/users/me/devices/" + deviceId + "/inventory")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());

        reportInventory(owner, null, "{\"profiles\":[]}").andExpect(status().isNotFound());
        reportInventory(owner, UUID.randomUUID(), "{\"profiles\":[]}")
                .andExpect(status().isNotFound());
        reportInventory(owner, installation, """
                {"profiles":[{"accountId":null,"name":"","syncState":"BOGUS","workspaces":[]}]}
                """).andExpect(status().isBadRequest());
        reportInventory(owner, installation, "{\"profiles\":[" + String.join(",",
                java.util.Collections.nCopies(201,
                        "{\"name\":\"P\",\"syncState\":\"SYNCHRONISED\",\"workspaces\":[]}"))
                + "]}").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------------- fixtures

    private UserEntity newAccountWithPassword() {
        final String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new UserEntity("inst-" + unique, unique + "@example.com",
                passwordEncoder.encode(PASSWORD), "Installations", UserAccountStatus.ACTIVE));
    }

    private String bearer(final UserEntity owner) {
        return "Bearer " + accessTokenIssuer.issueAccessTokenFor(owner);
    }

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

    private ResultActions refresh(final String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private void signOutDevice(final UserEntity owner, final String deviceId) throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/devices/" + deviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
    }

    private static String deviceIdOf(final MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.deviceId");
    }

    private static String refreshTokenOf(final MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");
    }

    private String mintConnectCode(final UserEntity owner) {
        return singleUseCodeService.mintExtensionConnectCode(owner).code();
    }

    private ResultActions redeemConnectCode(final String code, final UUID installation)
            throws Exception {

        return mockMvc.perform(post("/api/v1/auth/extension-connect")
                .header(ClientRequestHeaders.INSTALLATION_ID, installation.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions reportInventory(
            final UserEntity owner, final UUID installation, final String body) throws Exception {

        final var request = put("/api/v1/users/me/devices/current/inventory")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (installation != null) {
            request.header(ClientRequestHeaders.INSTALLATION_ID, installation.toString());
        }
        return mockMvc.perform(request);
    }

    private int countInstallationRows(final UUID ownerId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM user_device "
                + "WHERE user_id = ? AND installation_id IS NOT NULL", Integer.class, ownerId);
    }

    private int countLiveTokensOfInstallations(final UUID ownerId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_token token "
                + "JOIN user_device device ON device.id = token.device_id "
                + "WHERE token.user_id = ? AND token.revoked_at IS NULL "
                + "AND device.installation_id IS NOT NULL", Integer.class, ownerId);
    }
}
