package com.kovospace.newtablinks.fairuse.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kovospace.newtablinks.admin.services.AdminAccessTokenIssuer;
import com.kovospace.newtablinks.auth.services.AccessTokenIssuer;
import com.kovospace.newtablinks.common.MigratedPostgresDatabase;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The operator's Fair Use limits - global and per account - and the public plan limits, end to
 * end against the schema the migration image builds: the seeded row, the CHECKs, the cascade
 * with the account, and what reaches a signed-in account's own {@code /users/me/plan-limits}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FairUseLimitsAdministrationAgainstMigratedSchemaTest {

    private static final String GLOBAL_PATH = "/api/v1/admin/fair-use-limits";
    private static final String TODAYS_FAIR_USE = "{\"profiles\":50,\"workspacesPerProfile\":50,"
            + "\"groupsPerWorkspace\":25,\"subgroupsPerGroup\":25,\"linksPerWorkspace\":500,"
            + "\"closedTabs\":500,\"browsingHistory\":1000,\"devices\":100}";

    private static MigratedPostgresDatabase database;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

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

    /**
     * Puts today's Fair Use limits back, whatever an earlier test set.
     *
     * @throws Exception when the request fails
     */
    @BeforeEach
    void restoreTodaysFairUseLimits() throws Exception {
        mockMvc.perform(asAdmin(put(GLOBAL_PATH)).content(TODAYS_FAIR_USE))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the migration seeds today's Fair Use limits, and the public endpoint serves them "
            + "with the free plan's over them, without a token and ignoring a bad one")
    void shouldPublishTheSeededLimitsToAnybody() throws Exception {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT links_per_workspace FROM fair_use_limits WHERE id = 1", Integer.class))
                .isEqualTo(500);

        mockMvc.perform(get("/api/v1/plan-limits")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=60, public"))
                .andExpect(jsonPath("$.fairUse.profiles").value(50))
                .andExpect(jsonPath("$.fairUse.devices").value(100))
                .andExpect(jsonPath("$.free.profiles").value(1))
                .andExpect(jsonPath("$.free.workspacesPerProfile").value(2))
                .andExpect(jsonPath("$.free.linksPerWorkspace").value(500))
                .andExpect(jsonPath("$.free.devices").value(5));
    }

    @Test
    @DisplayName("the operator replaces the global limits, lowering included; a missing or "
            + "zero field is a 400 naming it; a user's token is refused")
    void shouldReplaceTheGlobalLimitsAndValidateThem() throws Exception {
        mockMvc.perform(asAdmin(put(GLOBAL_PATH)).content(TODAYS_FAIR_USE
                        .replace("\"linksPerWorkspace\":500", "\"linksPerWorkspace\":300")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linksPerWorkspace").value(300));
        mockMvc.perform(asAdmin(get(GLOBAL_PATH)))
                .andExpect(jsonPath("$.linksPerWorkspace").value(300));
        mockMvc.perform(get("/api/v1/plan-limits"))
                .andExpect(jsonPath("$.fairUse.linksPerWorkspace").value(300))
                .andExpect(jsonPath("$.free.linksPerWorkspace").value(300));

        mockMvc.perform(asAdmin(put(GLOBAL_PATH)).content(TODAYS_FAIR_USE
                        .replace("\"profiles\":50,", "").replace("\"devices\":100", "\"devices\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors", hasItem("profiles: must not be null")))
                .andExpect(jsonPath("$.validationErrors",
                        hasItem("devices: must be greater than or equal to 1")));

        final UserEntity user = newAccount();
        mockMvc.perform(get(GLOBAL_PATH).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessTokenIssuer.issueAccessTokenFor(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a raise for one account reaches its own plan limits and its admin row; one below "
            + "the global value is a 400; all-null removes it")
    void shouldRaiseOneAccountsLimitsAndTakeThemBack() throws Exception {
        final UserEntity user = newAccount();
        final String userPath = "/api/v1/admin/users/" + user.getId() + "/fair-use-limits";

        mockMvc.perform(asAdmin(get(userPath)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrides.linksPerWorkspace").isEmpty())
                .andExpect(jsonPath("$.note").isEmpty())
                .andExpect(jsonPath("$.premium").value(false))
                .andExpect(jsonPath("$.effective.profiles").value(1));

        mockMvc.perform(asAdmin(put(userPath)).content(
                        "{\"overrides\":{\"linksPerWorkspace\":499,\"devices\":101},\"note\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[0]").value(
                        "overrides.linksPerWorkspace: must be at least 500, the global Fair Use limit"));

        mockMvc.perform(asAdmin(put(userPath)).content(
                        "{\"overrides\":{\"linksPerWorkspace\":2000},\"note\":\"Teacher\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrides.linksPerWorkspace").value(2000))
                .andExpect(jsonPath("$.overrides.profiles").isEmpty())
                .andExpect(jsonPath("$.note").value("Teacher"))
                .andExpect(jsonPath("$.fairUse.linksPerWorkspace").value(500))
                .andExpect(jsonPath("$.effective.linksPerWorkspace").value(2000));
        mockMvc.perform(get("/api/v1/users/me/plan-limits").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessTokenIssuer.issueAccessTokenFor(user)))
                .andExpect(jsonPath("$.limits.linksPerWorkspace").value(2000))
                .andExpect(jsonPath("$.limits.profiles").value(1));
        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + user.getId())))
                .andExpect(jsonPath("$.fairUseOverride").value(true));

        mockMvc.perform(asAdmin(put(userPath)).content("{\"overrides\":{},\"note\":\"gone\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").isEmpty());
        assertThat(countOverridesOf(user.getId())).isZero();
        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + user.getId())))
                .andExpect(jsonPath("$.fairUseOverride").value(false));
    }

    @Test
    @DisplayName("a global value raised past an override wins")
    void shouldLetAHigherGlobalValueWinOverAnOverride() throws Exception {
        final UserEntity user = newAccount();
        final String userPath = "/api/v1/admin/users/" + user.getId() + "/fair-use-limits";
        mockMvc.perform(asAdmin(put(userPath)).content("{\"overrides\":{\"linksPerWorkspace\":600}}"))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(put(GLOBAL_PATH)).content(TODAYS_FAIR_USE
                        .replace("\"linksPerWorkspace\":500", "\"linksPerWorkspace\":800")))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(get(userPath)))
                .andExpect(jsonPath("$.overrides.linksPerWorkspace").value(600))
                .andExpect(jsonPath("$.effective.linksPerWorkspace").value(800));
    }

    @Test
    @DisplayName("DELETE answers 204 and removes the override; an unknown account is a 404; the "
            + "override goes with its account")
    void shouldDeleteAndCascade() throws Exception {
        final UserEntity user = newAccount();
        final String userPath = "/api/v1/admin/users/" + user.getId() + "/fair-use-limits";
        mockMvc.perform(asAdmin(put(userPath)).content("{\"overrides\":{\"devices\":150}}"))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(delete(userPath))).andExpect(status().isNoContent());
        assertThat(countOverridesOf(user.getId())).isZero();

        final String unknownPath = "/api/v1/admin/users/" + UUID.randomUUID() + "/fair-use-limits";
        mockMvc.perform(asAdmin(get(unknownPath))).andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(put(unknownPath)).content("{\"overrides\":{\"devices\":150}}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(delete(unknownPath))).andExpect(status().isNotFound());

        mockMvc.perform(asAdmin(put(userPath)).content("{\"overrides\":{\"devices\":150}}"))
                .andExpect(status().isOk());
        jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", user.getId());
        assertThat(countOverridesOf(user.getId())).isZero();
    }

    /**
     * Signs a request as the operator and marks its body as JSON.
     *
     * @param request the request
     * @return the same request, signed
     */
    private static MockHttpServletRequestBuilder asAdmin(final MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject("admin")).authorities(
                        new SimpleGrantedAuthority(AdminAccessTokenIssuer.ADMIN_AUTHORITY)));
    }

    /**
     * Creates a free, active account.
     *
     * @return the stored account
     */
    private UserEntity newAccount() {
        final String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new UserEntity("raise-" + unique, unique + "@example.com",
                null, "Raise", UserAccountStatus.ACTIVE));
    }

    /**
     * Counts an account's override rows straight from the database.
     *
     * @param userId identifier of the account
     * @return 0 or 1
     */
    private int countOverridesOf(final UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_fair_use_override WHERE user_id = ?", Integer.class, userId);
    }
}
