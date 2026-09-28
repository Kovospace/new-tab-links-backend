package com.kovospace.newtablinks.common;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Future;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A PostgreSQL whose schema was built by the migration image - never one Hibernate generated.
 *
 * <p>For tests whose subject is something only the migrated schema has: unique indexes, CHECK
 * constraints, delete rules, or native SQL such as {@code ON CONFLICT}. {@code ddl-auto=validate}
 * checks none of those, and a Hibernate-generated schema has none of them.</p>
 *
 * <p>Which image: the system property {@code newtablinks.migrations.image} when set - a published
 * tag such as {@code registry.matejkovac.sk/apps/new-tab-links-migrations:0.0.9}. Otherwise the
 * image is built from the Dockerfile of the local migrations checkout, found through
 * {@code newtablinks.migrations.directory} (default {@code ../new-tab-links-migrations}) - so it
 * carries whatever that checkout has on disk, including a migration not yet released. The calling
 * test is skipped, with the reason, when there is no Docker or no migrations source.</p>
 *
 * @since 0.0.11
 */
public final class MigratedPostgresDatabase {

    /** Must track the real database's major version, like the migrations repository's CI. */
    private static final String POSTGRES_IMAGE = "postgres:17-alpine";
    private static final String DATABASE_ALIAS = "postgres";

    private final Network network;
    private final PostgreSQLContainer postgres;

    /**
     * Wraps started containers.
     *
     * @param network  the network the database and the migration container share
     * @param postgres the started, migrated database
     */
    private MigratedPostgresDatabase(final Network network, final PostgreSQLContainer postgres) {
        this.network = network;
        this.postgres = postgres;
    }

    /**
     * Starts PostgreSQL and runs the migration image against it, skipping the calling test when
     * that is impossible here.
     *
     * @return the migrated database, ready for the application to connect to
     */
    public static MigratedPostgresDatabase startOrSkip() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available, so the schema cannot be built by the migration image");
        final Object migrationImage = resolveMigrationImage();

        final Network network = Network.newNetwork();
        final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE)
                .withNetwork(network)
                .withNetworkAliases(DATABASE_ALIAS);
        postgres.start();
        final MigratedPostgresDatabase database = new MigratedPostgresDatabase(network, postgres);
        database.runMigrations(migrationImage);
        return database;
    }

    /**
     * Stops the containers of a database that may never have started.
     *
     * @param database the database, or {@code null} when the test was skipped before starting it
     */
    public static void stop(final MigratedPostgresDatabase database) {
        if (database != null) {
            database.postgres.stop();
            database.network.close();
        }
    }

    /**
     * Points the application at this database, with Hibernate validating rather than changing
     * the schema - which on its own proves the entities match the migrations.
     *
     * @param registry the property registry of the test
     */
    public void registerDataSource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
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
    private void runMigrations(final Object migrationImage) {
        final GenericContainer<?> migrations = migrationImage instanceof String imageName
                ? new GenericContainer<>(imageName)
                : new GenericContainer<>((Future<String>) migrationImage);
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
