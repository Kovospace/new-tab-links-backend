package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.sync.dtos.DataChangedNotificationDto;
import com.kovospace.newtablinks.sync.events.UserDataChangedEvent;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells a user's connected browsers that their data changed, so they can pull a fresh snapshot.
 *
 * <p>This is the point of the websocket: without it a second browser shows stale links until
 * something makes it re-read.</p>
 *
 * @since 0.0.3
 */
@Service
public class UserRefreshNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserRefreshNotifier.class);

    /**
     * Destination each client subscribes to, resolved per user by the broker.
     */
    static final String REFRESH_DESTINATION = "/queue/refresh";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Creates the notifier.
     *
     * @param messagingTemplate sends to per-user destinations
     */
    public UserRefreshNotifier(final SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Sends the refresh signal once the change is actually durable.
     *
     * <p>Bound to {@link TransactionPhase#AFTER_COMMIT} deliberately. Notifying inside the
     * transaction would race the commit: a browser told to re-read could fetch a snapshot taken
     * before the change became visible and would then sit on stale data with no further signal
     * coming. Waiting also means a rolled back transaction sends nothing at all, which is
     * correct - nothing changed.</p>
     *
     * <p>A failure to deliver is logged and swallowed. The write has already succeeded and been
     * acknowledged; a broker problem must not surface as a failed request, and the client
     * recovers on its next poll or reconnect.</p>
     *
     * @param event the change that was committed
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyUserOfCommittedChange(final UserDataChangedEvent event) {
        try {
            messagingTemplate.convertAndSendToUser(
                    event.ownerId().toString(),
                    REFRESH_DESTINATION,
                    new DataChangedNotificationDto(Instant.now(), event.originDeviceId()));

            LOGGER.debug("Sent a refresh signal to account {}", event.ownerId());
        } catch (final RuntimeException deliveryFailed) {
            LOGGER.warn("Could not send a refresh signal to account {}", event.ownerId(),
                    deliveryFailed);
        }
    }
}
