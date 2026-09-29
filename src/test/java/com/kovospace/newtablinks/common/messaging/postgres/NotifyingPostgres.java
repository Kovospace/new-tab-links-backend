package com.kovospace.newtablinks.common.messaging.postgres;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.kovospace.newtablinks.common.messaging.MessageHandlerRegistry;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * A real PostgreSQL for the cross-replica messaging tests, and the "replicas" that listen to it.
 *
 * <p>Notifications are a server feature with no in-memory stand-in worth trusting, so these tests
 * run against the real thing - a plain database, since nothing here touches the schema. Each
 * replica is what a pod has: its own {@link MessageHandlerRegistry} and its own listening
 * connection, sharing only the database. Skipped, with the reason, when there is no Docker.</p>
 *
 * <p>Transactions run the way the application runs them - JPA over a Hikari pool - so that what
 * a test shows about a notification and the caller's transaction holds in production too.</p>
 *
 * @since 0.0.12
 */
public final class NotifyingPostgres implements AutoCloseable {

    /** Must track the real database's major version. */
    private static final String POSTGRES_IMAGE = "postgres:17-alpine";

    /** Marks the listening sessions, so a test can find and kill them. */
    private static final String LISTENER_APPLICATION_NAME = "notification-listener-under-test";

    /** Short, so that tests notice new subscriptions and lost connections quickly. */
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private static final Duration LISTENING_TIMEOUT = Duration.ofSeconds(15);

    private final PostgreSQLContainer postgres;
    private final HikariDataSource dataSource;
    private final LocalContainerEntityManagerFactoryBean entityManagerFactory;
    private final PlatformTransactionManager transactionManager;
    private final List<PostgresNotificationListener> startedReplicas = new ArrayList<>();

    /**
     * Wraps a started container.
     *
     * @param postgres the running database
     */
    private NotifyingPostgres(final PostgreSQLContainer postgres) {
        this.postgres = postgres;
        this.dataSource = new HikariDataSource();
        this.dataSource.setJdbcUrl(postgres.getJdbcUrl());
        this.dataSource.setUsername(postgres.getUsername());
        this.dataSource.setPassword(postgres.getPassword());
        this.entityManagerFactory = buildEntityManagerFactory(dataSource);
        this.transactionManager = new JpaTransactionManager(entityManagerFactory.getObject());
    }

    /**
     * An entity manager factory with no entities, which is all a JPA transaction needs.
     *
     * @param dataSource the pool
     * @return the initialised factory bean
     */
    private static LocalContainerEntityManagerFactoryBean buildEntityManagerFactory(final DataSource dataSource) {
        final LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.kovospace.newtablinks.common.messaging.postgres.none");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * Starts a database, or skips the calling test when Docker is not available.
     *
     * @return the running database
     */
    public static NotifyingPostgres startOrSkip() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for the PostgreSQL notification tests");
        final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);
        postgres.start();
        return new NotifyingPostgres(postgres);
    }

    /**
     * The transaction manager the publisher and a test's own transactions share.
     *
     * @return the transaction manager over this database
     */
    public PlatformTransactionManager transactionManager() {
        return transactionManager;
    }

    /**
     * A publisher, as a pod has one.
     *
     * @return the publisher
     */
    public PostgresNotifyMessagePublisher publisher() {
        return new PostgresNotifyMessagePublisher(
                new JdbcTemplate(dataSource), transactionManager, JsonMapper.builder().build());
    }

    /**
     * Starts one replica's listener for the given subscriptions.
     *
     * @param handlerRegistry that replica's subscriptions
     * @return the running listener, stopped by {@link #close()}
     */
    public PostgresNotificationListener startReplica(final MessageHandlerRegistry handlerRegistry) {
        final PostgresNotificationListener listener =
                new PostgresNotificationListener(this::openListenerConnection, handlerRegistry, POLL_INTERVAL);
        listener.start();
        startedReplicas.add(listener);
        return listener;
    }

    /**
     * Waits until a replica has run {@code LISTEN} for a topic, so a message published next
     * cannot be sent before anybody listens.
     *
     * @param listener  the replica
     * @param topicName the topic
     * @throws AssertionError when it is not listening within the timeout
     */
    public static void awaitListening(final PostgresNotificationListener listener, final String topicName) {
        final Instant giveUpAt = Instant.now().plus(LISTENING_TIMEOUT);
        while (!listener.isListeningTo(topicName)) {
            if (Instant.now().isAfter(giveUpAt)) {
                throw new AssertionError("Replica never started listening to " + topicName);
            }
            Thread.onSpinWait();
        }
    }

    /**
     * Kills every listening session from the server side, as a database restart or a dropped
     * network connection would.
     */
    public void terminateListeningSessions() {
        new JdbcTemplate(dataSource).query(
                "select pg_terminate_backend(pid) from pg_stat_activity where application_name = ?",
                resultSet -> { },
                LISTENER_APPLICATION_NAME);
    }

    /** Stops every replica and the database. */
    @Override
    public void close() {
        startedReplicas.forEach(PostgresNotificationListener::stop);
        entityManagerFactory.destroy();
        dataSource.close();
        postgres.stop();
    }

    /**
     * Opens a listening connection, named so that it can be found in {@code pg_stat_activity}.
     *
     * @return the connection
     * @throws java.sql.SQLException when the database cannot be reached
     */
    private java.sql.Connection openListenerConnection() throws java.sql.SQLException {
        final Properties connectionProperties = new Properties();
        connectionProperties.setProperty("user", postgres.getUsername());
        connectionProperties.setProperty("password", postgres.getPassword());
        connectionProperties.setProperty("ApplicationName", LISTENER_APPLICATION_NAME);
        return DriverManager.getConnection(postgres.getJdbcUrl(), connectionProperties);
    }
}
