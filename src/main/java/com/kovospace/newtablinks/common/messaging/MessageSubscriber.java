package com.kovospace.newtablinks.common.messaging;

import java.util.function.Consumer;

/**
 * Receives the messages published to a topic by any instance of this service.
 *
 * <p>The receiving half of the messaging port; the delivery guarantees are the ones
 * {@link MessagePublisher} states. Handlers run on the transport's own thread, one message at a
 * time, so a handler should hand anything slow to an executor rather than hold every other topic
 * up. A handler that throws is logged and does not affect the others.</p>
 *
 * @since 0.0.12
 */
public interface MessageSubscriber {

    /**
     * Registers a handler for a topic.
     *
     * <p>May be called before or after the transport has started; a subscription made while it
     * is running takes effect within one polling interval, and messages sent before then are not
     * delivered to it.</p>
     *
     * @param topic     the topic to receive
     * @param handler   called with each message's payload
     * @param <PAYLOAD> the topic's payload type
     */
    <PAYLOAD> void subscribe(MessageTopic<PAYLOAD> topic, Consumer<PAYLOAD> handler);
}
