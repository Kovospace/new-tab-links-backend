package com.kovospace.newtablinks.common.messaging.postgres;

import com.kovospace.newtablinks.common.messaging.MessagePublisher;
import com.kovospace.newtablinks.common.messaging.MessageTopic;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes through PostgreSQL's {@code NOTIFY}: every session that has run {@code LISTEN} on the
 * topic's channel - one per running replica - is sent the payload.
 *
 * <p><b>Always in a transaction of its own</b> ({@code REQUIRES_NEW}). PostgreSQL delivers a
 * notification when the transaction that raised it commits, and never if it rolls back. Joining
 * the caller's transaction would therefore make a message wait for the caller's commit and vanish
 * with its rollback - the opposite of what {@link MessagePublisher} promises, and of what a
 * message broker, which takes no part in a database transaction, would do. With a transaction of
 * its own it is sent the moment {@code publish} returns, from inside a transaction or after one.</p>
 *
 * <p>{@code pg_notify()} rather than the {@code NOTIFY} statement, because the statement takes
 * neither its channel nor its payload as a bind parameter.</p>
 *
 * @since 0.0.12
 */
public final class PostgresNotifyMessagePublisher implements MessagePublisher {

    /**
     * PostgreSQL refuses a payload of 8000 bytes or more. Checked here so an oversized message
     * fails with a reason rather than with a server error.
     */
    static final int MAXIMUM_PAYLOAD_BYTES = 7999;

    private static final String NOTIFY_STATEMENT = "select pg_notify(?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate separateTransaction;
    private final ObjectMapper objectMapper;

    /**
     * Creates the publisher.
     *
     * @param jdbcTemplate       runs the notification
     * @param transactionManager opens the separate transaction it runs in
     * @param objectMapper       writes payloads as JSON
     */
    public PostgresNotifyMessagePublisher(
            final JdbcTemplate jdbcTemplate,
            final PlatformTransactionManager transactionManager,
            final ObjectMapper objectMapper) {

        this.jdbcTemplate = jdbcTemplate;
        this.separateTransaction = new TransactionTemplate(transactionManager);
        this.separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException when the JSON payload reaches PostgreSQL's 8000-byte limit
     */
    @Override
    public <PAYLOAD> void publish(final MessageTopic<PAYLOAD> topic, final PAYLOAD payload) {
        final String jsonPayload = objectMapper.writeValueAsString(payload);
        final int payloadBytes = jsonPayload.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "A message on topic '%s' is %d bytes; PostgreSQL notifications carry at most %d"
                            .formatted(topic.name(), payloadBytes, MAXIMUM_PAYLOAD_BYTES));
        }
        separateTransaction.executeWithoutResult(status ->
                jdbcTemplate.query(NOTIFY_STATEMENT, resultSet -> { }, topic.name(), jsonPayload));
    }
}
