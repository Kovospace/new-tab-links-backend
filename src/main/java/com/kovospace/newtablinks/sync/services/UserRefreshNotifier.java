package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.common.messaging.MessagePublisher;
import com.kovospace.newtablinks.sync.events.UserDataChangedEvent;
import com.kovospace.newtablinks.sync.events.UserDataChangedMessage;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Announces a committed change to every replica, so that the user's connected browsers - on
 * whichever pods they are connected to - can pull a fresh snapshot.
 *
 * <p>This is the point of the websocket: without it a second browser shows stale links until
 * something makes it re-read.</p>
 *
 * <p>It does not send to the websocket itself. It used to, and the simple broker only knows the
 * sessions of the pod it runs in: with two replicas, a change committed on one pod never reached
 * a browser connected to the other. It now publishes {@link UserDataChangedMessage} to every
 * replica, and {@link UserRefreshRelay} in each of them delivers to its own sessions.</p>
 *
 * @since 0.0.3
 */
@Service
public class UserRefreshNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserRefreshNotifier.class);

    private final MessagePublisher messagePublisher;

    /**
     * Creates the notifier.
     *
     * @param messagePublisher reaches every replica
     */
    public UserRefreshNotifier(final MessagePublisher messagePublisher) {
        this.messagePublisher = messagePublisher;
    }

    /**
     * Announces the change once it is actually durable.
     *
     * <p>Bound to {@link TransactionPhase#AFTER_COMMIT} deliberately. Notifying inside the
     * transaction would race the commit: a browser told to re-read could fetch a snapshot taken
     * before the change became visible and would then sit on stale data with no further signal
     * coming. Waiting also means a rolled back transaction sends nothing at all, which is
     * correct - nothing changed.</p>
     *
     * <p>A failure to publish is logged and swallowed. The write has already succeeded and been
     * acknowledged; a messaging problem must not surface as a failed request, and the client
     * recovers on its next pull.</p>
     *
     * @param event the change that was committed
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyUserOfCommittedChange(final UserDataChangedEvent event) {
        try {
            messagePublisher.publish(UserDataChangedMessage.TOPIC,
                    new UserDataChangedMessage(event.ownerId(), event.originDeviceId(), Instant.now()));

            LOGGER.debug("Announced a change to account {} to every replica", event.ownerId());
        } catch (final RuntimeException publishingFailed) {
            LOGGER.warn("Could not announce a change to account {}", event.ownerId(),
                    publishingFailed);
        }
    }
}
