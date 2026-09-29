package com.kovospace.newtablinks.common.messaging;

import com.kovospace.newtablinks.common.messaging.postgres.PostgresNotificationListener;
import com.kovospace.newtablinks.common.messaging.postgres.PostgresNotifyMessagePublisher;
import java.sql.DriverManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Chooses the transport replicas message each other over - the one place that knows.
 *
 * <p>Everything else depends on {@link MessagePublisher} and {@link MessageSubscriber} only. The
 * subscriber side is always the transport-neutral {@link MessageHandlerRegistry}; a transport
 * contributes a publisher and something that feeds the registry what it receives. Switching to a
 * message broker is a new pair of beans here behind another
 * {@link MessagingProperties.MessageTransport} value.</p>
 *
 * @since 0.0.12
 */
@Configuration
public class MessagingConfiguration {

    /** Property selecting the transport, matched case-insensitively. */
    private static final String TRANSPORT_PROPERTY = "newtablinks.messaging.transport";

    /**
     * The subscriptions, whatever the transport.
     *
     * @param objectMapper reads received payloads
     * @return the registry, which is also the application's {@link MessageSubscriber}
     */
    @Bean
    public MessageHandlerRegistry messageHandlerRegistry(final ObjectMapper objectMapper) {
        return new MessageHandlerRegistry(objectMapper);
    }

    /**
     * Publishes with PostgreSQL {@code NOTIFY}.
     *
     * @param jdbcTemplate       runs the notification
     * @param transactionManager gives each notification a transaction of its own
     * @param objectMapper       writes payloads
     * @return the publisher
     */
    @Bean
    @ConditionalOnProperty(name = TRANSPORT_PROPERTY, havingValue = "postgres", matchIfMissing = true)
    public MessagePublisher postgresNotifyMessagePublisher(
            final JdbcTemplate jdbcTemplate,
            final PlatformTransactionManager transactionManager,
            final ObjectMapper objectMapper) {

        return new PostgresNotifyMessagePublisher(jdbcTemplate, transactionManager, objectMapper);
    }

    /**
     * Receives PostgreSQL notifications on a connection of its own, outside the pool.
     *
     * @param connectionDetails   the same database the pool connects to
     * @param handlerRegistry     where received messages go
     * @param messagingProperties the poll interval
     * @return the listener, started and stopped with the application
     */
    @Bean
    @ConditionalOnProperty(name = TRANSPORT_PROPERTY, havingValue = "postgres", matchIfMissing = true)
    public PostgresNotificationListener postgresNotificationListener(
            final JdbcConnectionDetails connectionDetails,
            final MessageHandlerRegistry handlerRegistry,
            final MessagingProperties messagingProperties) {

        return new PostgresNotificationListener(
                () -> DriverManager.getConnection(
                        connectionDetails.getJdbcUrl(),
                        connectionDetails.getUsername(),
                        connectionDetails.getPassword()),
                handlerRegistry,
                messagingProperties.postgresPollInterval());
    }
}
