package com.kovospace.newtablinks.common.messaging;

/**
 * Sends a message to every running instance of this service.
 *
 * <p>The application's side of the messaging port. Which transport carries the message is
 * decided in {@link MessagingConfiguration} and nowhere else, so moving from PostgreSQL
 * notifications to a message broker is a new implementation and a property, not a change to any
 * caller.</p>
 *
 * <p>The contract every implementation keeps:</p>
 * <ul>
 *   <li><b>Every instance receives it, the publishing one included.</b></li>
 *   <li><b>At most once.</b> An instance that is restarting or reconnecting when a message is
 *       sent misses it; nothing is stored or replayed. Use it for hints a receiver can recover
 *       from missing - "go and re-read" - never for the only copy of something.</li>
 *   <li><b>Sent now, independently of any transaction the caller is in.</b> A caller that must
 *       not announce an uncommitted change publishes after the commit, as the sync notifier does.
 *       Keeping the transport out of the caller's transaction is what lets a broker, which has
 *       no part in a database transaction, keep the same promise.</li>
 *   <li><b>No ordering</b> between messages from different instances.</li>
 * </ul>
 *
 * @since 0.0.12
 */
public interface MessagePublisher {

    /**
     * Publishes a message to a topic.
     *
     * @param topic     where it goes
     * @param payload   what it carries, serialised as JSON
     * @param <PAYLOAD> the topic's payload type
     * @throws IllegalArgumentException when the payload is too large for the transport
     * @throws RuntimeException         when the transport cannot be reached; the caller decides
     *                                  whether a lost hint is worth failing over
     */
    <PAYLOAD> void publish(MessageTopic<PAYLOAD> topic, PAYLOAD payload);
}
