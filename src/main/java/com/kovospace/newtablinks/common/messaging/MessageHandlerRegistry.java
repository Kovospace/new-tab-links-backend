package com.kovospace.newtablinks.common.messaging;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Who listens to which topic, and the delivery of a received message to them.
 *
 * <p>Transport-neutral on purpose: a transport's only jobs are to listen on
 * {@link #subscribedTopicNames()} and to pass what arrives to {@link #dispatch}. The decoding,
 * the fan-out to several handlers and the isolation of a failing one are the same whatever
 * carried the message, so they live here once rather than in each transport.</p>
 *
 * <p>Safe to use from several threads: subscriptions are made by application components while
 * the transport's thread is already dispatching.</p>
 *
 * @since 0.0.12
 */
public final class MessageHandlerRegistry implements MessageSubscriber {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageHandlerRegistry.class);

    private final ObjectMapper objectMapper;
    private final Map<String, List<TopicHandler<?>>> handlersByTopicName = new ConcurrentHashMap<>();

    /**
     * Creates an empty registry.
     *
     * @param objectMapper reads payloads out of their JSON form
     */
    public MessageHandlerRegistry(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException when the topic's name is already registered with a
     *                                  different payload type
     */
    @Override
    public <PAYLOAD> void subscribe(final MessageTopic<PAYLOAD> topic, final Consumer<PAYLOAD> handler) {
        final List<TopicHandler<?>> handlers = handlersByTopicName
                .computeIfAbsent(topic.name(), topicName -> new CopyOnWriteArrayList<>());
        final boolean payloadTypeConflicts = handlers.stream()
                .anyMatch(registered -> !registered.topic().equals(topic));
        if (payloadTypeConflicts) {
            throw new IllegalArgumentException(
                    "Topic '%s' is already subscribed with another payload type".formatted(topic.name()));
        }
        handlers.add(new TopicHandler<>(topic, handler));
    }

    /**
     * The topics at least one handler listens to, for the transport to listen on.
     *
     * @return an unmodifiable snapshot of the topic names
     */
    public Set<String> subscribedTopicNames() {
        return Set.copyOf(handlersByTopicName.keySet());
    }

    /**
     * Delivers a received message to every handler of its topic.
     *
     * <p>A message for a topic nobody here listens to is ignored, as is one whose payload does
     * not parse - another replica running a newer version may be publishing a shape this one
     * does not know yet. A handler that throws is logged and the rest still run.</p>
     *
     * @param topicName   the topic the transport received it on
     * @param jsonPayload the payload exactly as published
     */
    public void dispatch(final String topicName, final String jsonPayload) {
        for (final TopicHandler<?> topicHandler : handlersByTopicName.getOrDefault(topicName, List.of())) {
            deliverIsolated(topicHandler, jsonPayload);
        }
    }

    /**
     * Delivers to one handler, so that its failure cannot reach the others or the transport.
     *
     * @param topicHandler the handler with its topic
     * @param jsonPayload  the payload as published
     */
    private void deliverIsolated(final TopicHandler<?> topicHandler, final String jsonPayload) {
        try {
            topicHandler.deliver(objectMapper, jsonPayload);
        } catch (final JacksonException unreadablePayload) {
            LOGGER.warn("Ignored a message on topic {} whose payload could not be read",
                    topicHandler.topic().name(), unreadablePayload);
        } catch (final RuntimeException handlerFailed) {
            LOGGER.error("A handler of topic {} failed", topicHandler.topic().name(), handlerFailed);
        }
    }

    /**
     * One handler, kept with the topic that says how to decode what it receives.
     *
     * @param topic     the topic, carrying the payload type
     * @param handler   the application's callback
     * @param <PAYLOAD> the payload type
     */
    private record TopicHandler<PAYLOAD>(MessageTopic<PAYLOAD> topic, Consumer<PAYLOAD> handler) {

        /**
         * Decodes the payload and calls the handler.
         *
         * @param objectMapper reads the JSON
         * @param jsonPayload  the payload as published
         * @throws JacksonException when the payload does not match the topic's type
         */
        void deliver(final ObjectMapper objectMapper, final String jsonPayload) {
            handler.accept(objectMapper.readValue(jsonPayload, topic.payloadType()));
        }
    }
}
