package com.kovospace.newtablinks.payment.controllers;

import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.WEBHOOK_SECRET;
import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.lifetimeCheckoutCompleted;
import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.sign;
import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.subscriptionCheckoutCompleted;
import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.subscriptionEvent;
import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.utf8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.exceptions.PaymentProviderRequestFailedException;
import com.kovospace.newtablinks.payment.models.SubscriptionCancellationResult;
import com.kovospace.newtablinks.payment.services.PaymentSubscriptionCanceller;
import com.kovospace.newtablinks.payment.services.SupersededSubscriptionCancellationService;
import com.kovospace.newtablinks.payment.services.CreemWebhookInterpreter;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import com.kovospace.newtablinks.user.services.UserService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Exercises the Creem webhook end to end against a PostgreSQL schema built by the migration
 * image - never one Hibernate generated.
 *
 * <p>That is the point of this test, not a detail of it. The replay protection is a unique index,
 * the lifecycle values are CHECK constraints, and the account link cascades; {@code
 * ddl-auto=validate} checks none of those, and a Hibernate-generated schema has none of them. So
 * this runs the real migrations in the real image against a real PostgreSQL, and then starts the
 * application with {@code ddl-auto=validate} - which on its own proves the new entities match
 * {@code V10}.</p>
 *
 * <p>Which image: the system property {@code newtablinks.migrations.image} when set - a published
 * tag such as {@code registry.matejkovac.sk/apps/new-tab-links-migrations:0.0.9}. Otherwise the
 * image is built from the Dockerfile of the local migrations checkout, found through
 * {@code newtablinks.migrations.directory} (default {@code ../new-tab-links-migrations}). The
 * test is skipped - loudly, with the reason - when there is no Docker or no migrations source.</p>
 *
 * @since 0.0.9
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreemWebhookAgainstMigratedSchemaTest {

    /** Must track the real database's major version, like the migrations repository's CI. */
    private static final String POSTGRES_IMAGE = "postgres:17-alpine";
    private static final String DATABASE_ALIAS = "postgres";
    private static final Instant T0 = Instant.parse("2026-09-27T10:00:00Z");
    private static final Instant FIRST_PERIOD_END = Instant.parse("2027-09-27T10:00:00Z");
    private static final Instant SECOND_PERIOD_END = Instant.parse("2028-09-27T10:00:00Z");

    private static Network network;
    private static PostgreSQLContainer postgres;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private SupersededSubscriptionCancellationService supersededSubscriptionCancellationService;

    /** Stands in for Creem's cancel endpoint; disabled unless a test says otherwise. */
    @MockitoBean
    private PaymentSubscriptionCanceller paymentSubscriptionCanceller;

    /**
     * Starts PostgreSQL and runs the migration image against it, before the application starts.
     */
    @BeforeAll
    static void startDatabaseBuiltByTheMigrationImage() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available, so the schema cannot be built by the migration image");
        final Object migrationImage = resolveMigrationImage();

        network = Network.newNetwork();
        postgres = new PostgreSQLContainer(POSTGRES_IMAGE)
                .withNetwork(network)
                .withNetworkAliases(DATABASE_ALIAS);
        postgres.start();
        runMigrations(migrationImage);
    }

    /**
     * Stops the containers.
     */
    @AfterAll
    static void stopDatabase() {
        if (postgres != null) {
            postgres.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    /**
     * Points the application at the migrated database and gives it a webhook secret.
     *
     * @param registry the property registry
     */
    @DynamicPropertySource
    static void useTheMigratedDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres.getUsername());
        registry.add("spring.datasource.password", () -> postgres.getPassword());
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("newtablinks.payment.creem.webhook-secret", () -> WEBHOOK_SECRET);
        registry.add("newtablinks.payment.creem.api-key", () -> "");
    }

    @Test
    @DisplayName("a signed delivery with no bearer token gets through security and makes the "
            + "account lifetime pro, recording what was charged")
    void shouldApplyASignedLifetimePurchaseWithoutAnyToken() throws Exception {
        final UUID accountId = newAccount();
        final byte[] body = utf8(lifetimeCheckoutCompleted("evt_" + accountId, T0, accountId));

        deliver(body, sign(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.outcome").value("APPLIED"));

        final Map<String, Object> entitlement = entitlementOf(accountId);
        assertThat(entitlement.get("source")).isEqualTo("LIFETIME");
        assertThat(entitlement.get("status")).isEqualTo("ACTIVE");
        assertThat(entitlement.get("paid_until")).isNull();
        assertThat(entitlement.get("charged_amount_minor_units")).isEqualTo(1814L);
        assertThat(entitlement.get("charged_currency")).isEqualTo("EUR");
        assertThat(entitlement.get("provider_order_id")).isEqualTo("ord_lifetime_1");
        assertThat(entitlement.get("payment_provider")).isEqualTo("CREEM");
    }

    @Test
    @DisplayName("a replayed delivery is acknowledged, claimed once, and changes nothing")
    void shouldAcknowledgeAReplayWithoutApplyingItTwice() throws Exception {
        final UUID accountId = newAccount();
        final String eventId = "evt_replay_" + accountId;
        final byte[] body = utf8(lifetimeCheckoutCompleted(eventId, T0, accountId));
        deliver(body, sign(body)).andExpect(status().isOk());
        final Object updatedAtAfterFirstDelivery = entitlementOf(accountId).get("updated_at");

        deliver(body, sign(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        assertThat(claimRowsOf(eventId)).isEqualTo(1);
        assertThat(entitlementOf(accountId).get("updated_at")).isEqualTo(updatedAtAfterFirstDelivery);
        assertThat(jdbcTemplate.queryForObject(
                "select outcome from payment_webhook_event where provider_event_id = ?",
                String.class, eventId)).isEqualTo("APPLIED");
    }

    @Test
    @DisplayName("the migrated schema itself refuses a second claim of the same event")
    void shouldHaveTheUniqueIndexTheReplayGuardDependsOn() {
        final String eventId = "evt_index_" + UUID.randomUUID();
        insertClaimRow(eventId);

        assertThatThrownBy(() -> insertClaimRow(eventId))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("concurrent redeliveries of one event apply it exactly once")
    void shouldApplyConcurrentRedeliveriesExactlyOnce() throws Exception {
        final UUID accountId = newAccount();
        final String eventId = "evt_race_" + accountId;
        final byte[] body = utf8(lifetimeCheckoutCompleted(eventId, T0, accountId));

        final List<MvcResult> results = deliverConcurrently(body, 6);

        final long applied = results.stream()
                .filter(result -> result.getResponse().getStatus() == 200)
                .filter(result -> contentOf(result).contains("\"duplicate\":false"))
                .count();
        final long refusedOtherwise = results.stream()
                .filter(result -> result.getResponse().getStatus() != 200
                        && result.getResponse().getStatus() != 409)
                .count();
        assertThat(applied).isEqualTo(1);
        assertThat(refusedOtherwise).isZero();
        assertThat(claimRowsOf(eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("an older paid event arriving after a newer past-due is refused, and past-due "
            + "keeps the period already paid for")
    void shouldRefuseAnOlderEventDeliveredLate() throws Exception {
        final UUID accountId = newAccount();
        final String subscriptionId = "sub_" + accountId;
        deliverSigned(subscriptionEvent("subscription.paid", "evt_paid1_" + accountId, T0,
                accountId, subscriptionId, FIRST_PERIOD_END));
        deliverSigned(subscriptionEvent("subscription.past_due", "evt_due_" + accountId,
                T0.plus(Duration.ofDays(365)), accountId, subscriptionId, SECOND_PERIOD_END));

        final byte[] late = utf8(subscriptionEvent("subscription.paid", "evt_paid0_" + accountId,
                T0.minusSeconds(60), accountId, subscriptionId, SECOND_PERIOD_END));
        deliver(late, sign(late))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED_STALE"));

        final Map<String, Object> entitlement = entitlementOf(accountId);
        assertThat(entitlement.get("status")).isEqualTo("PAST_DUE");
        assertThat(((Timestamp) entitlement.get("paid_until")).toInstant()).isEqualTo(FIRST_PERIOD_END);
    }

    @Test
    @DisplayName("a lifetime purchase over a subscription commits, then cancels the subscription, "
            + "and that subscription's late canceled event leaves lifetime intact")
    void shouldCancelTheSupersededSubscriptionAfterCommit() throws Exception {
        final UUID accountId = newAccount();
        final String subscriptionId = "sub_" + accountId;
        when(paymentSubscriptionCanceller.isEnabled()).thenReturn(true);
        when(paymentSubscriptionCanceller.cancelImmediately(subscriptionId))
                .thenReturn(SubscriptionCancellationResult.CANCELLED);
        deliverSigned(subscriptionCheckoutCompleted("evt_sub_" + accountId, T0, accountId,
                subscriptionId, FIRST_PERIOD_END));

        deliverSigned(lifetimeCheckoutCompleted("evt_life_" + accountId, T0.plusSeconds(60),
                accountId));

        verify(paymentSubscriptionCanceller).cancelImmediately(subscriptionId);
        final Map<String, Object> afterLifetime = entitlementOf(accountId);
        assertThat(afterLifetime.get("source")).isEqualTo("LIFETIME");
        assertThat(afterLifetime.get("provider_subscription_id")).isNull();
        assertThat(afterLifetime.get("superseded_subscription_id")).isEqualTo(subscriptionId);
        assertThat(afterLifetime.get("superseded_subscription_cancelled_at")).isNotNull();

        final byte[] lateCancel = utf8(subscriptionEvent("subscription.canceled",
                "evt_cancel_" + accountId, T0.plusSeconds(120), accountId, subscriptionId,
                FIRST_PERIOD_END));
        deliver(lateCancel, sign(lateCancel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED_UNRELATED"));
        assertThat(entitlementOf(accountId).get("source")).isEqualTo("LIFETIME");
        assertThat(entitlementOf(accountId).get("status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("a failed cancellation still stores the lifetime purchase, stays pending, and the "
            + "retry job completes it")
    void shouldKeepAFailedCancellationPendingForTheRetryJob() throws Exception {
        final UUID accountId = newAccount();
        final String subscriptionId = "sub_" + accountId;
        when(paymentSubscriptionCanceller.isEnabled()).thenReturn(true);
        when(paymentSubscriptionCanceller.cancelImmediately(subscriptionId))
                .thenThrow(new PaymentProviderRequestFailedException("Creem is down", null))
                .thenReturn(SubscriptionCancellationResult.CANCELLED);
        deliverSigned(subscriptionCheckoutCompleted("evt_sub_" + accountId, T0, accountId,
                subscriptionId, FIRST_PERIOD_END));

        final byte[] lifetime = utf8(lifetimeCheckoutCompleted("evt_life_" + accountId,
                T0.plusSeconds(60), accountId));
        deliver(lifetime, sign(lifetime))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPLIED"));

        assertThat(entitlementOf(accountId).get("source")).isEqualTo("LIFETIME");
        assertThat(entitlementOf(accountId).get("superseded_subscription_cancelled_at")).isNull();

        supersededSubscriptionCancellationService.retryPendingCancellations();

        assertThat(entitlementOf(accountId).get("superseded_subscription_id"))
                .isEqualTo(subscriptionId);
        assertThat(entitlementOf(accountId).get("superseded_subscription_cancelled_at"))
                .isNotNull();
    }

    @Test
    @DisplayName("a lifetime purchase delivered after a newer subscription.paid is applied, not "
            + "refused as stale")
    void shouldApplyALifetimePurchaseDeliveredAfterANewerSubscriptionEvent() throws Exception {
        final UUID accountId = newAccount();
        final String subscriptionId = "sub_" + accountId;
        final Instant paidAt = T0.plus(Duration.ofDays(1));
        deliverSigned(subscriptionEvent("subscription.paid", "evt_paid_" + accountId, paidAt,
                accountId, subscriptionId, FIRST_PERIOD_END));

        final byte[] lateLifetime = utf8(lifetimeCheckoutCompleted("evt_life_" + accountId, T0,
                accountId));
        deliver(lateLifetime, sign(lateLifetime))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPLIED"));

        final Map<String, Object> entitlement = entitlementOf(accountId);
        assertThat(entitlement.get("source")).isEqualTo("LIFETIME");
        assertThat(entitlement.get("status")).isEqualTo("ACTIVE");
        assertThat(entitlement.get("superseded_subscription_id")).isEqualTo(subscriptionId);
        assertThat(((Timestamp) entitlement.get("last_provider_event_at")).toInstant())
                .isEqualTo(paidAt);
    }

    @Test
    @DisplayName("the schema refuses a cancellation time without the subscription it belongs to")
    void shouldRefuseACancellationTimeWithoutItsSubscription() throws Exception {
        final UUID accountId = newAccount();
        deliverSigned(lifetimeCheckoutCompleted("evt_" + accountId, T0, accountId));

        assertThatThrownBy(() -> jdbcTemplate.update("update user_entitlement set "
                + "superseded_subscription_cancelled_at = now() where user_id = ?", accountId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_user_entitlement_superseded_pair");
    }

    @Test
    @DisplayName("a delivery with a wrong or missing signature is refused before anything is stored")
    void shouldRefuseAnUnsignedOrTamperedDelivery() throws Exception {
        final UUID accountId = newAccount();
        final String eventId = "evt_forged_" + accountId;
        final byte[] body = utf8(lifetimeCheckoutCompleted(eventId, T0, accountId));
        final byte[] tampered = utf8(new String(body, java.nio.charset.StandardCharsets.UTF_8)
                .replace("Kováč", "Kovac"));

        deliver(body, "0".repeat(64)).andExpect(status().isUnauthorized());
        deliver(body, null).andExpect(status().isUnauthorized());
        deliver(tampered, sign(body)).andExpect(status().isUnauthorized());

        assertThat(claimRowsOf(eventId)).isZero();
        assertThat(entitlementRowsOf(accountId)).isZero();
    }

    @Test
    @DisplayName("deleting the account removes its entitlement with it")
    void shouldDeleteTheEntitlementWithTheAccount() throws Exception {
        final UUID accountId = newAccount();
        deliverSigned(lifetimeCheckoutCompleted("evt_delete_" + accountId, T0, accountId));

        userService.deleteUser(accountId);

        assertThat(entitlementRowsOf(accountId)).isZero();
    }

    @Test
    @DisplayName("opening a checkout needs a signed-in account, and says so when payments are off")
    void shouldGuardTheCheckoutEndpoint() throws Exception {
        final String request = "{\"plan\":\"LIFETIME\"}";

        mockMvc.perform(post("/api/v1/payments/checkouts")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/payments/checkouts")
                        .with(jwt().jwt(token -> token.subject(newAccount().toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isServiceUnavailable());
    }

    /**
     * Posts a body to the webhook endpoint, with no bearer token.
     *
     * @param body      the raw body
     * @param signature the signature header, or {@code null} for none
     * @return the result actions
     * @throws Exception when the request cannot be performed
     */
    private org.springframework.test.web.servlet.ResultActions deliver(
            final byte[] body, final String signature) throws Exception {

        final var request = post(ApiEndpointPaths.CREEM_WEBHOOK_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (signature != null) {
            request.header(CreemWebhookInterpreter.SIGNATURE_HEADER, signature);
        }
        return mockMvc.perform(request);
    }

    /**
     * Posts a correctly signed body and expects it to be accepted.
     *
     * @param json the body
     * @throws Exception when the request cannot be performed
     */
    private void deliverSigned(final String json) throws Exception {
        final byte[] body = utf8(json);
        deliver(body, sign(body)).andExpect(status().isOk());
    }

    /**
     * Posts the same signed body from several threads at once.
     *
     * @param body       the raw body
     * @param deliveries how many concurrent deliveries
     * @return every result
     * @throws Exception when a delivery cannot be performed
     */
    private List<MvcResult> deliverConcurrently(final byte[] body, final int deliveries)
            throws Exception {

        final CountDownLatch startTogether = new CountDownLatch(1);
        final List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int index = 0; index < deliveries; index++) {
            tasks.add(() -> {
                startTogether.await();
                return deliver(body, sign(body)).andReturn();
            });
        }
        try (ExecutorService executor = Executors.newFixedThreadPool(deliveries)) {
            final List<Future<MvcResult>> futures = new ArrayList<>();
            tasks.forEach(task -> futures.add(executor.submit(task)));
            startTogether.countDown();
            final List<MvcResult> results = new ArrayList<>();
            for (final Future<MvcResult> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    /**
     * Creates an active account.
     *
     * @return its identifier
     */
    private UUID newAccount() {
        final String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new UserEntity("buyer-" + unique, unique + "@example.com",
                null, "Buyer", UserAccountStatus.ACTIVE)).getId();
    }

    /**
     * Reads an account's entitlement row straight from the database.
     *
     * @param accountId the account
     * @return the row
     */
    private Map<String, Object> entitlementOf(final UUID accountId) {
        return jdbcTemplate.queryForMap(
                "select * from user_entitlement where user_id = ?", accountId);
    }

    /**
     * Counts an account's entitlement rows.
     *
     * @param accountId the account
     * @return zero or one
     */
    private int entitlementRowsOf(final UUID accountId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from user_entitlement where user_id = ?", Integer.class, accountId);
    }

    /**
     * Counts the claims of one event.
     *
     * @param eventId the provider's event identifier
     * @return how many claim rows exist
     */
    private int claimRowsOf(final String eventId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from payment_webhook_event where provider_event_id = ?",
                Integer.class, eventId);
    }

    /**
     * Inserts a claim row with plain SQL, bypassing the application.
     *
     * @param eventId the provider's event identifier
     */
    private void insertClaimRow(final String eventId) {
        jdbcTemplate.update("insert into payment_webhook_event (id, payment_provider, "
                        + "provider_event_id, event_type, created_at, updated_at) "
                        + "values (?, 'CREEM', ?, 'checkout.completed', now(), now())",
                UUID.randomUUID(), eventId);
    }

    /**
     * Reads a response body.
     *
     * @param result the result
     * @return the body as text
     */
    private static String contentOf(final MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (final java.io.UnsupportedEncodingException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    /**
     * Finds the migration image to run: a named one, or one built from the local checkout.
     *
     * @return an image name, or a future building one
     */
    private static Object resolveMigrationImage() {
        final String namedImage = System.getProperty("newtablinks.migrations.image", "");
        if (!namedImage.isBlank()) {
            return namedImage;
        }
        final Path migrationsDirectory = Path.of(System.getProperty(
                "newtablinks.migrations.directory", "../new-tab-links-migrations"));
        assumeTrue(Files.isRegularFile(migrationsDirectory.resolve("Dockerfile")),
                "No migrations checkout at " + migrationsDirectory.toAbsolutePath()
                        + " and no -Dnewtablinks.migrations.image given, so the schema cannot "
                        + "be built by the migration image");
        return new ImageFromDockerfile("new-tab-links-migrations-under-test", true)
                .withFileFromPath(".", migrationsDirectory);
    }

    /**
     * Runs the migration image to completion against the database container.
     *
     * @param migrationImage an image name, or a future building one
     */
    @SuppressWarnings("unchecked")
    private static void runMigrations(final Object migrationImage) {
        final GenericContainer<?> migrations = migrationImage instanceof String imageName
                ? new GenericContainer<>(imageName)
                : new GenericContainer<>((java.util.concurrent.Future<String>) migrationImage);
        try (migrations) {
            migrations.withNetwork(network)
                    .withEnv("FLYWAY_URL", "jdbc:postgresql://%s:5432/%s"
                            .formatted(DATABASE_ALIAS, postgres.getDatabaseName()))
                    .withEnv("FLYWAY_USER", postgres.getUsername())
                    .withEnv("FLYWAY_PASSWORD", postgres.getPassword())
                    .withEnv("FLYWAY_CONNECT_RETRIES", "10")
                    .withStartupCheckStrategy(
                            new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(3)))
                    .start();
        }
    }
}
