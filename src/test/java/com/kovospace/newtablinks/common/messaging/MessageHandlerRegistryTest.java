package com.kovospace.newtablinks.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the transport-neutral half of messaging: decoding, fan-out to handlers, and keeping a
 * bad message or a failing handler from reaching anything else.
 *
 * @since 0.0.12
 */
class MessageHandlerRegistryTest {

    private static final MessageTopic<CountMessage> COUNTS =
            new MessageTopic<>("test_counts", CountMessage.class);

    private final MessageHandlerRegistry registry = new MessageHandlerRegistry(JsonMapper.builder().build());

    @Test
    @DisplayName("a received message is decoded and given to every handler of its topic")
    void shouldDeliverToEveryHandlerOfTheTopic() {
        final List<CountMessage> first = new ArrayList<>();
        final List<CountMessage> second = new ArrayList<>();
        registry.subscribe(COUNTS, first::add);
        registry.subscribe(COUNTS, second::add);

        registry.dispatch("test_counts", "{\"count\":3}");

        assertThat(first).containsExactly(new CountMessage(3));
        assertThat(second).containsExactly(new CountMessage(3));
        assertThat(registry.subscribedTopicNames()).containsExactly("test_counts");
    }

    @Test
    @DisplayName("a handler that throws does not stop the next one")
    void shouldIsolateAFailingHandler() {
        final List<CountMessage> received = new ArrayList<>();
        registry.subscribe(COUNTS, message -> {
            throw new IllegalStateException("handler broke");
        });
        registry.subscribe(COUNTS, received::add);

        registry.dispatch("test_counts", "{\"count\":1}");

        assertThat(received).containsExactly(new CountMessage(1));
    }

    @Test
    @DisplayName("an unreadable payload, or a topic nobody listens to, is ignored")
    void shouldIgnoreWhatItCannotDeliver() {
        final List<CountMessage> received = new ArrayList<>();
        registry.subscribe(COUNTS, received::add);

        registry.dispatch("test_counts", "not json");
        registry.dispatch("some_other_topic", "{\"count\":1}");

        assertThat(received).isEmpty();
    }

    @Test
    @DisplayName("one topic name cannot be subscribed with two payload types")
    void shouldRefuseAConflictingPayloadType() {
        registry.subscribe(COUNTS, message -> { });

        assertThatThrownBy(() -> registry.subscribe(
                new MessageTopic<>("test_counts", String.class), message -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a topic name that is not portable across transports is refused")
    void shouldRefuseAnUnportableTopicName() {
        assertThatThrownBy(() -> new MessageTopic<>("Bad-Name; drop table", String.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A payload for the tests' own topic.
     *
     * @param count a number
     */
    record CountMessage(int count) {
    }
}
