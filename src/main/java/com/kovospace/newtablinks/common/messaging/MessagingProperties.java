package com.kovospace.newtablinks.common.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How replicas of this service message each other, bound from {@code newtablinks.messaging.*}.
 *
 * @param transport            what carries the messages; {@link MessagingConfiguration} builds
 *                             the beans for the one named here
 * @param postgresPollInterval the longest a replica waits for a PostgreSQL notification before
 *                             checking its connection and picking up new subscriptions; it does
 *                             not delay delivery, which is immediate
 * @since 0.0.12
 */
@ConfigurationProperties(prefix = "newtablinks.messaging")
public record MessagingProperties(
        MessageTransport transport,
        Duration postgresPollInterval) {

    /**
     * The transports a deployment can choose.
     *
     * <p>Only PostgreSQL today. A message broker is added as a value here, an implementation of
     * {@link MessagePublisher} and a listener feeding {@link MessageHandlerRegistry}, and its
     * beans in {@link MessagingConfiguration} - no caller changes.</p>
     */
    public enum MessageTransport {

        /** {@code NOTIFY}/{@code LISTEN} on the application's own database. */
        POSTGRES
    }
}
