package com.kovospace.newtablinks.statistics.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kovospace.newtablinks.admin.services.AdminAccessTokenIssuer;
import com.kovospace.newtablinks.common.MigratedPostgresDatabase;
import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.statistics.services.WebsiteVisitService;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Exercises the usage statistics end to end against the schema the migration image builds.
 *
 * <p>The subject is native SQL - two {@code ON CONFLICT} upserts - and a {@code CHECK} constraint
 * on the metric names, none of which a Hibernate-generated schema would have. Starting with
 * {@code ddl-auto=validate} also proves the entities match {@code V12}.</p>
 *
 * @since 0.0.11
 */
@SpringBootTest
@AutoConfigureMockMvc
class UsageStatisticsAgainstMigratedSchemaTest {

    private static final int CONFIGURED_INTERVAL_SECONDS = 1234;
    private static final int CONFIGURED_RATE_LIMIT = 5;
    private static final String BROWSER =
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0";
    private static final String OTHER_BROWSER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/129.0.0.0 Safari/537.36";

    private static MigratedPostgresDatabase database;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebsiteVisitService websiteVisitService;

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
     * Points the application at the migrated database, with a recognisable interval and a limit
     * small enough to reach.
     *
     * @param registry the property registry
     */
    @DynamicPropertySource
    static void useTheMigratedDatabase(final DynamicPropertyRegistry registry) {
        database.registerDataSource(registry);
        registry.add("newtablinks.statistics.report-interval-seconds",
                () -> CONFIGURED_INTERVAL_SECONDS);
        registry.add("newtablinks.statistics.rate-limit-maximum-requests",
                () -> CONFIGURED_RATE_LIMIT);
    }

    /**
     * Starts every test from empty tables.
     */
    @BeforeEach
    void emptyTheStatisticsTables() {
        jdbcTemplate.update("DELETE FROM daily_metric");
        jdbcTemplate.update("DELETE FROM website_visitor_hash");
    }

    @Test
    @DisplayName("two reports for one day add up, and the configured interval reaches the answer")
    void shouldSumTwoReportsForOneDay() throws Exception {
        final LocalDate today = today();

        reportNewTabs("198.51.100.1", "{\"days\":[{\"day\":\"%s\",\"count\":12}]}".formatted(today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextReportAfterSeconds").value(CONFIGURED_INTERVAL_SECONDS));
        reportNewTabs("198.51.100.1", "{\"days\":[{\"day\":\"%s\",\"count\":3}]}".formatted(today))
                .andExpect(status().isOk());

        assertThat(storedTotal(today, "new_tabs")).isEqualTo(15L);
    }

    @Test
    @DisplayName("out-of-range days and counts are dropped and the report still answers 200")
    void shouldDropOutOfRangeEntriesAndStillSucceed() throws Exception {
        final LocalDate today = today();
        final String report = ("{\"days\":["
                + "{\"day\":\"%s\",\"count\":4},"
                + "{\"day\":\"%s\",\"count\":9},"
                + "{\"day\":\"%s\",\"count\":9},"
                + "{\"day\":\"%s\",\"count\":0},"
                + "{\"day\":\"%s\",\"count\":2001}]}")
                .formatted(today, today.minusDays(8), today.plusDays(2), today.minusDays(1), today);

        reportNewTabs("198.51.100.2", report)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextReportAfterSeconds").value(CONFIGURED_INTERVAL_SECONDS));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM daily_metric", Long.class)).isEqualTo(1L);
        assertThat(storedTotal(today, "new_tabs")).isEqualTo(4L);
    }

    @Test
    @DisplayName("the same address and browser count once a day; another browser counts again")
    void shouldCountSameVisitorOncePerDay() throws Exception {
        visitWebsite("203.0.113.10", BROWSER).andExpect(status().isNoContent());
        visitWebsite("203.0.113.10", BROWSER).andExpect(status().isNoContent());
        assertThat(storedTotal(today(), "website_visitors")).isEqualTo(1L);

        visitWebsite("203.0.113.10", OTHER_BROWSER).andExpect(status().isNoContent());
        assertThat(storedTotal(today(), "website_visitors")).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM website_visitor_hash", Long.class)).isEqualTo(2L);
    }

    @Test
    @DisplayName("a bot or a request with no user agent answers 204 and counts nothing")
    void shouldNotCountBots() throws Exception {
        visitWebsite("203.0.113.11", "Mozilla/5.0 (compatible; Googlebot/2.1)")
                .andExpect(status().isNoContent());
        mockMvc.perform(post(ApiEndpointPaths.WEBSITE_VISIT_STATISTICS_PATH)
                        .with(fromAddress("203.0.113.11")))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM daily_metric", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM website_visitor_hash", Long.class)).isZero();
    }

    @Test
    @DisplayName("an address over the limit is answered 429 and its report is not counted")
    void shouldRateLimitPerAddress() throws Exception {
        final String report = "{\"days\":[{\"day\":\"%s\",\"count\":1}]}".formatted(today());
        for (int request = 0; request < CONFIGURED_RATE_LIMIT; request++) {
            reportNewTabs("192.0.2.99", report).andExpect(status().isOk());
        }

        reportNewTabs("192.0.2.99", report).andExpect(status().isTooManyRequests());
        assertThat(storedTotal(today(), "new_tabs")).isEqualTo((long) CONFIGURED_RATE_LIMIT);
    }

    @Test
    @DisplayName("the admin month has every day, zero where nothing was counted, per metric")
    void shouldFillMissingDaysWithZeroForAdmin() throws Exception {
        insertTotal("2026-02-03", "new_tabs", 5);
        insertTotal("2026-02-28", "new_tabs", 7);
        insertTotal("2026-02-03", "website_visitors", 100);

        mockMvc.perform(get(ApiEndpointPaths.ADMINISTRATION_BASE_PATH
                        + ApiEndpointPaths.ADMINISTRATION_METRICS_SUBPATH)
                        .param("metric", "new_tabs")
                        .param("month", "2026-02")
                        .with(jwt().jwt(token -> token.subject("admin")).authorities(
                                new SimpleGrantedAuthority(AdminAccessTokenIssuer.ADMIN_AUTHORITY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value("new_tabs"))
                .andExpect(jsonPath("$.month").value("2026-02"))
                .andExpect(jsonPath("$.days.length()").value(28))
                .andExpect(jsonPath("$.days[0].day").value("2026-02-01"))
                .andExpect(jsonPath("$.days[0].value").value(0))
                .andExpect(jsonPath("$.days[2].value").value(5))
                .andExpect(jsonPath("$.days[27].value").value(7))
                .andExpect(jsonPath("$.total").value(12));
    }

    @Test
    @DisplayName("the cleanup deletes the previous days' visitor hashes and keeps today's")
    void shouldForgetPreviousDaysVisitorHashes() throws Exception {
        visitWebsite("203.0.113.12", BROWSER).andExpect(status().isNoContent());
        jdbcTemplate.update("INSERT INTO website_visitor_hash (day, visitor_hash) VALUES (?, ?)",
                today().minusDays(1), new byte[] {1, 2, 3});

        assertThat(websiteVisitService.forgetVisitorsBeforeToday()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM website_visitor_hash WHERE day = ?", Long.class, today()))
                .isEqualTo(1L);
    }

    /**
     * Posts a new-tab report from an address.
     *
     * @param address the caller's address
     * @param json    the body
     * @return the result, for expectations
     * @throws Exception when the request cannot be performed
     */
    private ResultActions reportNewTabs(final String address, final String json) throws Exception {
        return mockMvc.perform(post(ApiEndpointPaths.NEW_TAB_STATISTICS_PATH)
                .with(fromAddress(address))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /**
     * Posts a website visit from an address and browser.
     *
     * @param address   the caller's address
     * @param userAgent the caller's browser
     * @return the result, for expectations
     * @throws Exception when the request cannot be performed
     */
    private ResultActions visitWebsite(final String address, final String userAgent)
            throws Exception {

        return mockMvc.perform(post(ApiEndpointPaths.WEBSITE_VISIT_STATISTICS_PATH)
                .with(fromAddress(address))
                .header(HttpHeaders.USER_AGENT, userAgent));
    }

    /**
     * Makes a request appear to come from an address.
     *
     * @param address the address
     * @return the request post-processor
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor fromAddress(
            final String address) {

        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    /**
     * Reads a day's stored total.
     *
     * @param day    the day
     * @param metric the metric identifier
     * @return the total, or {@code null} when the day has no row
     */
    private Long storedTotal(final LocalDate day, final String metric) {
        return jdbcTemplate.query(
                "SELECT value FROM daily_metric WHERE day = ? AND metric = ?",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                day, metric);
    }

    /**
     * Writes a day's total directly.
     *
     * @param day    the day, as {@code YYYY-MM-DD}
     * @param metric the metric identifier
     * @param total  the total
     */
    private void insertTotal(final String day, final String metric, final long total) {
        jdbcTemplate.update("INSERT INTO daily_metric (day, metric, value) VALUES (?, ?, ?)",
                LocalDate.parse(day), metric, total);
    }

    /**
     * Returns the server's date, as the statistics module decides it.
     *
     * @return today in UTC
     */
    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
