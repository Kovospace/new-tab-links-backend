package com.kovospace.newtablinks.common.messaging;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A named stream of messages that every running instance of this service receives.
 *
 * <p>"Topic" in the message-broker sense: publish once, and each subscriber - on every pod - gets
 * its own copy. That is fan-out, not a work queue; nothing here makes instances compete for a
 * message, and a use that needs that wants a different contract rather than this one stretched.</p>
 *
 * <p>The name is restricted to lower-case letters, digits and underscores, at most 63 characters:
 * what a PostgreSQL channel identifier allows unquoted, and a safe exchange or topic name on any
 * broker this might move to. It is part of the wire contract between replicas, so renaming one
 * during a rolling deploy splits old pods from new ones until the rollout finishes.</p>
 *
 * @param name        the topic's name on the transport
 * @param payloadType the record carried, written as JSON
 * @param <PAYLOAD>   the payload type
 * @since 0.0.12
 */
public record MessageTopic<PAYLOAD>(String name, Class<PAYLOAD> payloadType) {

    /** Unquoted PostgreSQL identifier rules, which also suit any broker. */
    private static final Pattern PORTABLE_TOPIC_NAME = Pattern.compile("[a-z][a-z0-9_]{0,62}");

    /**
     * Validates the topic.
     *
     * @throws IllegalArgumentException when the name is not portable across transports
     * @throws NullPointerException     when either component is missing
     */
    public MessageTopic {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(payloadType, "payloadType");
        if (!PORTABLE_TOPIC_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Topic name '%s' must match %s".formatted(name, PORTABLE_TOPIC_NAME.pattern()));
        }
    }
}
