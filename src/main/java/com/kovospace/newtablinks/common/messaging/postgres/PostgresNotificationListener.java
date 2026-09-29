package com.kovospace.newtablinks.common.messaging.postgres;

import com.kovospace.newtablinks.common.messaging.MessageHandlerRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Holds this replica's {@code LISTEN} session and hands each notification to the
 * {@link MessageHandlerRegistry}.
 *
 * <p><b>One dedicated connection, outside the pool.</b> {@code LISTEN} belongs to a database
 * session, so the connection has to stay open and unshared for as long as the replica runs. A
 * pooled one would be a pool slot lost for good, and the pool would retire it at its maximum
 * lifetime - silently ending the subscription.</p>
 *
 * <p><b>Polled, on one thread.</b> The driver buffers notifications as they arrive and
 * {@link PGConnection#getNotifications(int)} waits up to the poll interval for one. JDBC
 * connections are not thread-safe, so every statement on this one - {@code LISTEN} for a topic
 * subscribed while running, the liveness check - happens on this thread between waits.</p>
 *
 * <p><b>Reconnects with backoff.</b> A lost connection is reopened and every topic listened to
 * again. What was published in between is lost, as {@code MessagePublisher} warns; a waiting read
 * on a connection whose peer vanished without closing it would never notice, which is what the
 * liveness check after every empty wait is for.</p>
 *
 * @since 0.0.12
 */
public final class PostgresNotificationListener implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresNotificationListener.class);

    private static final String LISTENER_THREAD_NAME = "postgres-notification-listener";
    private static final int LIVENESS_CHECK_TIMEOUT_SECONDS = 5;
    private static final Duration FIRST_RECONNECT_DELAY = Duration.ofSeconds(1);
    private static final Duration LONGEST_RECONNECT_DELAY = Duration.ofSeconds(30);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);

    private final DedicatedConnectionOpener connectionOpener;
    private final MessageHandlerRegistry handlerRegistry;
    private final Duration pollInterval;

    /** The topics the current session has run {@code LISTEN} for; emptied when it is lost. */
    private final Set<String> listenedTopicNames = ConcurrentHashMap.newKeySet();

    private volatile boolean running;
    private volatile Thread listenerThread;

    /**
     * Creates the listener; nothing is opened until {@link #start()}.
     *
     * @param connectionOpener opens the dedicated connection, and reopens it after a loss
     * @param handlerRegistry  says which topics to listen to and receives what arrives
     * @param pollInterval     the longest wait for a notification before checking the connection
     *                         and picking up newly subscribed topics
     */
    public PostgresNotificationListener(
            final DedicatedConnectionOpener connectionOpener,
            final MessageHandlerRegistry handlerRegistry,
            final Duration pollInterval) {

        this.connectionOpener = connectionOpener;
        this.handlerRegistry = handlerRegistry;
        this.pollInterval = pollInterval;
    }

    /** Starts the listening thread. */
    @Override
    public void start() {
        running = true;
        listenerThread = Thread.ofPlatform()
                .name(LISTENER_THREAD_NAME)
                .daemon(true)
                .start(this::listenUntilStopped);
    }

    /**
     * Stops listening and waits, up to a bound, for the thread to close its connection.
     */
    @Override
    public void stop() {
        running = false;
        final Thread thread = listenerThread;
        if (thread == null) {
            return;
        }
        thread.interrupt();
        try {
            thread.join(STOP_TIMEOUT);
        } catch (final InterruptedException interruptedWhileStopping) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Whether the listening thread has been started and not stopped.
     *
     * @return {@code true} between {@link #start()} and {@link #stop()}
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Whether this replica is receiving a topic right now.
     *
     * <p>False from the moment a connection is lost until the next one has listened again - so
     * it answers "would a message published now reach this replica", as nearly as can be known.</p>
     *
     * @param topicName the topic's name
     * @return {@code true} while the current session listens to it
     */
    public boolean isListeningTo(final String topicName) {
        return listenedTopicNames.contains(topicName);
    }

    /**
     * Keeps a listening session open until stopped, reopening it after every loss.
     */
    private void listenUntilStopped() {
        Duration reconnectDelay = FIRST_RECONNECT_DELAY;
        while (running) {
            try (Connection connection = connectionOpener.open()) {
                LOGGER.info("Listening for cross-replica messages");
                reconnectDelay = FIRST_RECONNECT_DELAY;
                receiveUntilStoppedOrLost(connection);
            } catch (final SQLException connectionLost) {
                // Now, not in finally: that runs after the backoff below, during which this
                // replica must already stop claiming to listen.
                listenedTopicNames.clear();
                if (!running) {
                    return;
                }
                LOGGER.warn("Lost the notification connection; reconnecting in {}",
                        reconnectDelay, connectionLost);
                sleepQuietly(reconnectDelay);
                reconnectDelay = longerDelay(reconnectDelay);
            } finally {
                // Closed on stop: that session's LISTENs are gone with it.
                listenedTopicNames.clear();
            }
        }
    }

    /**
     * Receives and dispatches on one connection until it fails or the listener is stopped.
     *
     * @param connection the dedicated connection
     * @throws SQLException when the connection is lost
     */
    private void receiveUntilStoppedOrLost(final Connection connection) throws SQLException {
        final PGConnection postgresConnection = connection.unwrap(PGConnection.class);
        while (running) {
            listenToNewlySubscribedTopics(connection);
            final PGNotification[] notifications =
                    postgresConnection.getNotifications((int) pollInterval.toMillis());
            if (notifications == null || notifications.length == 0) {
                requireLiveConnection(connection);
                continue;
            }
            for (final PGNotification notification : notifications) {
                handlerRegistry.dispatch(notification.getName(), notification.getParameter());
            }
        }
    }

    /**
     * Runs {@code LISTEN} for every subscribed topic this session is not listening to yet.
     *
     * <p>Topic names are safe to put in the statement unescaped: {@code MessageTopic} admits only
     * lower-case letters, digits and underscores. They are quoted all the same.</p>
     *
     * @param connection the dedicated connection
     * @throws SQLException when the connection is lost
     */
    private void listenToNewlySubscribedTopics(final Connection connection) throws SQLException {

        for (final String topicName : handlerRegistry.subscribedTopicNames()) {
            if (listenedTopicNames.contains(topicName)) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("LISTEN \"" + topicName + "\"");
            }
            listenedTopicNames.add(topicName);
        }
    }

    /**
     * Makes a silently dead connection fail, so that it is replaced.
     *
     * @param connection the dedicated connection
     * @throws SQLException when it no longer answers
     */
    private static void requireLiveConnection(final Connection connection) throws SQLException {
        if (!connection.isValid(LIVENESS_CHECK_TIMEOUT_SECONDS)) {
            throw new SQLException("The notification connection stopped answering");
        }
    }

    /**
     * Doubles a reconnection delay up to its ceiling.
     *
     * @param currentDelay the delay just waited
     * @return the next one
     */
    private static Duration longerDelay(final Duration currentDelay) {
        final Duration doubled = currentDelay.multipliedBy(2);
        return doubled.compareTo(LONGEST_RECONNECT_DELAY) > 0 ? LONGEST_RECONNECT_DELAY : doubled;
    }

    /**
     * Waits before reconnecting; being interrupted means the listener is stopping.
     *
     * @param delay how long to wait
     */
    private static void sleepQuietly(final Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (final InterruptedException stopping) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Opens the listener's own connection, bypassing the pool.
     *
     * @since 0.0.12
     */
    @FunctionalInterface
    public interface DedicatedConnectionOpener {

        /**
         * Opens a new connection the caller owns and closes.
         *
         * @return the connection
         * @throws SQLException when the database cannot be reached
         */
        Connection open() throws SQLException;
    }
}
