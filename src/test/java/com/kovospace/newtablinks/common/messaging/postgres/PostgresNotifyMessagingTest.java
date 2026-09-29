package com.kovospace.newtablinks.common.messaging.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kovospace.newtablinks.common.messaging.MessageHandlerRegistry;
import com.kovospace.newtablinks.common.messaging.MessageTopic;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the PostgreSQL transport against a real server: what one replica publishes, every
 * replica receives - including after its connection was lost, and independently of whatever
 * transaction the publisher happens to be in.
 *
 * @since 0.0.12
 */
class PostgresNotifyMessagingTest {

    private static final MessageTopic<GreetingMessage> GREETINGS =
            new MessageTopic<>("test_greetings", GreetingMessage.class);

    private static final long DELIVERY_TIMEOUT_SECONDS = 10;

    private NotifyingPostgres database;

    @BeforeEach
    void startDatabase() {
        database = NotifyingPostgres.startOrSkip();
    }

    @AfterEach
    void stopDatabase() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    @DisplayName("a message published by one replica reaches every replica, the publisher included")
    void shouldDeliverToEveryReplica() throws InterruptedException {
        final BlockingQueue<GreetingMessage> receivedByFirst = new LinkedBlockingQueue<>();
        final BlockingQueue<GreetingMessage> receivedBySecond = new LinkedBlockingQueue<>();
        startListeningReplica(receivedByFirst);
        startListeningReplica(receivedBySecond);

        database.publisher().publish(GREETINGS, new GreetingMessage("hello"));

        assertThat(receivedByFirst.poll(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(new GreetingMessage("hello"));
        assertThat(receivedBySecond.poll(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(new GreetingMessage("hello"));
    }

    /** The sync notifier publishes from an AFTER_COMMIT listener; that path has to deliver. */
    @Test
    @DisplayName("a message published after another transaction committed is delivered")
    void shouldDeliverWhenPublishedFromAnAfterCommitCallback() throws InterruptedException {
        final BlockingQueue<GreetingMessage> received = new LinkedBlockingQueue<>();
        startListeningReplica(received);
        final PostgresNotifyMessagePublisher publisher = database.publisher();

        new TransactionTemplate(database.transactionManager()).executeWithoutResult(status ->
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publisher.publish(GREETINGS, new GreetingMessage("after commit"));
                    }
                }));

        assertThat(received.poll(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(new GreetingMessage("after commit"));
    }

    /**
     * The port promises "sent now, independently of the caller's transaction", which is what a
     * broker does. A NOTIFY that joined the caller's transaction would instead wait for its commit
     * and vanish with its rollback - so the two transports would behave differently.
     */
    @Test
    @DisplayName("a message published inside a transaction that then rolls back is still delivered")
    void shouldStayOutOfTheCallersTransaction() throws InterruptedException {
        final BlockingQueue<GreetingMessage> received = new LinkedBlockingQueue<>();
        startListeningReplica(received);
        final PostgresNotifyMessagePublisher publisher = database.publisher();

        new TransactionTemplate(database.transactionManager()).executeWithoutResult(status -> {
            publisher.publish(GREETINGS, new GreetingMessage("inside a rolled back transaction"));
            status.setRollbackOnly();
        });

        assertThat(received.poll(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(new GreetingMessage("inside a rolled back transaction"));
    }

    @Test
    @DisplayName("a replica whose connection was killed listens again and receives what follows")
    void shouldResumeListeningAfterTheConnectionIsLost() throws InterruptedException {
        final BlockingQueue<GreetingMessage> received = new LinkedBlockingQueue<>();
        final PostgresNotificationListener replica = startListeningReplica(received);

        database.terminateListeningSessions();
        awaitNotListening(replica);
        NotifyingPostgres.awaitListening(replica, GREETINGS.name());
        database.publisher().publish(GREETINGS, new GreetingMessage("after reconnecting"));

        assertThat(received.poll(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(new GreetingMessage("after reconnecting"));
    }

    @Test
    @DisplayName("a payload PostgreSQL cannot carry is refused with a reason, not a server error")
    void shouldRefuseAnOversizedPayload() {
        final GreetingMessage oversized =
                new GreetingMessage("x".repeat(PostgresNotifyMessagePublisher.MAXIMUM_PAYLOAD_BYTES));

        assertThatThrownBy(() -> database.publisher().publish(GREETINGS, oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test_greetings");
    }

    /**
     * Starts a replica collecting greetings, and waits until it listens.
     *
     * @param received where it puts what arrives
     * @return the replica's listener
     */
    private PostgresNotificationListener startListeningReplica(final BlockingQueue<GreetingMessage> received) {
        final MessageHandlerRegistry registry = new MessageHandlerRegistry(JsonMapper.builder().build());
        registry.subscribe(GREETINGS, received::add);
        final PostgresNotificationListener replica = database.startReplica(registry);
        NotifyingPostgres.awaitListening(replica, GREETINGS.name());
        return replica;
    }

    /**
     * Waits until a replica has noticed that its session is gone.
     *
     * @param replica the replica whose session was killed
     */
    private static void awaitNotListening(final PostgresNotificationListener replica) {
        final long giveUpAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(DELIVERY_TIMEOUT_SECONDS);
        while (replica.isListeningTo(GREETINGS.name())) {
            if (System.nanoTime() > giveUpAt) {
                throw new AssertionError("Replica never noticed its session was killed");
            }
            Thread.onSpinWait();
        }
    }

    /**
     * A payload for the tests' own topic.
     *
     * @param text what it says
     */
    record GreetingMessage(String text) {
    }
}
