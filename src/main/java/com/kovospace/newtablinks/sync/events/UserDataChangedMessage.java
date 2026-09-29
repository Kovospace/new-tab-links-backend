package com.kovospace.newtablinks.sync.events;

import com.kovospace.newtablinks.common.messaging.MessageTopic;
import java.time.Instant;
import java.util.UUID;

/**
 * Tells every replica that an account's data changed, so that whichever of them holds that
 * account's websocket connections can pass it on.
 *
 * <p>The replica that committed the change is rarely the only one with the account's browsers
 * connected: each browser keeps its socket open to whichever pod it happened to reach. This
 * message is what crosses from the one to the others.</p>
 *
 * @param ownerId        the account whose data changed
 * @param originDeviceId the device that caused it, or {@code null} when not known
 * @param changedAt      when the change was committed
 * @since 0.0.12
 */
public record UserDataChangedMessage(UUID ownerId, String originDeviceId, Instant changedAt) {

    /** The topic every replica publishes these to and listens on. */
    public static final MessageTopic<UserDataChangedMessage> TOPIC =
            new MessageTopic<>("newtablinks_user_data_changed", UserDataChangedMessage.class);
}
