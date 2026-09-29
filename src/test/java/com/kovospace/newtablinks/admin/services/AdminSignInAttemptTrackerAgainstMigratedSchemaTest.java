package com.kovospace.newtablinks.admin.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.kovospace.newtablinks.common.MigratedPostgresDatabase;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Exercises the operator sign-in lockout against the schema the migration image builds.
 *
 * <p>The counting rules live in native SQL on the {@code admin_sign_in_lock} table since
 * {@code V13}, so this is where they are proved. The one that matters most is the reason the
 * table exists: a failure counted on one replica counts on every other. It used to be a field on
 * this bean, and two replicas meant two counters and twice the guesses.</p>
 *
 * @since 0.0.13
 */
@SpringBootTest
class AdminSignInAttemptTrackerAgainstMigratedSchemaTest {

    /** Small enough to reach quickly. */
    private static final int MAXIMUM_ATTEMPTS = 3;

    /** Short enough to wait out in a test. */
    private static final Duration LOCK_DURATION = Duration.ofSeconds(1);

    private static MigratedPostgresDatabase database;

    @Autowired
    private AdminSignInAttemptTracker tracker;

    @Autowired
    private AutowireCapableBeanFactory beanFactory;

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
     * Points the application at the migrated database, with a lockout small enough to reach.
     *
     * @param registry the property registry
     */
    @DynamicPropertySource
    static void useTheMigratedDatabase(final DynamicPropertyRegistry registry) {
        database.registerDataSource(registry);
        registry.add("newtablinks.admin.maximum-consecutive-failed-attempts", () -> MAXIMUM_ATTEMPTS);
        registry.add("newtablinks.admin.failed-attempts-lock-duration", LOCK_DURATION::toString);
    }

    /**
     * Starts every test with no failures recorded.
     */
    @BeforeEach
    void forgetEveryFailure() {
        jdbcTemplate.update("DELETE FROM admin_sign_in_lock");
    }

    @Test
    @DisplayName("sign-in locks once the configured number of failures is reached, and not before")
    void shouldLockAfterTooManyFailures() {
        for (int attempt = 1; attempt < MAXIMUM_ATTEMPTS; attempt++) {
            tracker.recordFailure();
            assertThat(tracker.remainingLock()).isZero();
        }

        tracker.recordFailure();

        assertThat(tracker.remainingLock()).isPositive().isLessThanOrEqualTo(LOCK_DURATION);
    }

    @Test
    @DisplayName("failures counted on one replica count on another")
    void shouldShareTheCountBetweenReplicas() {
        final AdminSignInAttemptTracker otherReplica = anotherReplica();

        for (int attempt = 1; attempt < MAXIMUM_ATTEMPTS; attempt++) {
            tracker.recordFailure();
        }
        otherReplica.recordFailure();

        assertThat(tracker.remainingLock()).isPositive();
        assertThat(otherReplica.remainingLock()).isPositive();
    }

    @Test
    @DisplayName("failures recorded at the same moment on two replicas are all counted")
    void shouldCountConcurrentFailuresWithoutLosingAny() throws Exception {
        final AdminSignInAttemptTracker otherReplica = anotherReplica();
        final int failures = 40;
        final List<Callable<Void>> attempts = IntStream.range(0, failures)
                .<Callable<Void>>mapToObj(index -> () -> {
                    (index % 2 == 0 ? tracker : otherReplica).recordFailure();
                    return null;
                })
                .toList();

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (final var outcome : pool.invokeAll(attempts)) {
                outcome.get();
            }
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT consecutive_failures FROM admin_sign_in_lock", Integer.class))
                .isEqualTo(failures);
    }

    @Test
    @DisplayName("a success clears the failures behind it")
    void shouldForgetFailuresAfterASuccess() {
        for (int attempt = 1; attempt < MAXIMUM_ATTEMPTS; attempt++) {
            tracker.recordFailure();
        }

        tracker.recordSuccess();
        tracker.recordFailure();

        assertThat(tracker.remainingLock()).isZero();
    }

    @Test
    @DisplayName("a lock that has run out is forgotten, failures and all")
    void shouldForgetAnExpiredLock() throws InterruptedException {
        for (int attempt = 0; attempt < MAXIMUM_ATTEMPTS; attempt++) {
            tracker.recordFailure();
        }
        assertThat(tracker.remainingLock()).isPositive();

        Thread.sleep(LOCK_DURATION.plusMillis(200));

        assertThat(tracker.remainingLock()).isZero();
        tracker.recordFailure();
        assertThat(tracker.remainingLock()).isZero();
    }

    /**
     * A second tracker, as a second pod would have: its own bean, the same database.
     *
     * @return a tracker with the same transactional proxy as the application's own
     */
    private AdminSignInAttemptTracker anotherReplica() {
        return beanFactory.createBean(AdminSignInAttemptTracker.class);
    }
}
