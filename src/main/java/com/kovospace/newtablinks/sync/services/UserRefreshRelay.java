package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.common.messaging.MessageSubscriber;
import com.kovospace.newtablinks.sync.dtos.DataChangedNotificationDto;
import com.kovospace.newtablinks.sync.events.UserDataChangedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Passes every replica's change announcements to the browsers connected to this one.
 *
 * <p>Runs in every pod. The simple broker keeps each browser's subscription in the pod the
 * browser connected to, so delivery has to happen there; a pod with no session for the account
 * sends to nobody, which costs nothing.</p>
 *
 * @since 0.0.12
 */
@Service
public class UserRefreshRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserRefreshRelay.class);

    /** Destination each client subscribes to, resolved per user by the broker. */
    static final String REFRESH_DESTINATION = "/queue/refresh";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Creates the relay and subscribes it to change announcements.
     *
     * @param messagingTemplate sends to this pod's per-user destinations
     * @param messageSubscriber delivers every replica's announcements
     */
    public UserRefreshRelay(
            final SimpMessagingTemplate messagingTemplate,
            final MessageSubscriber messageSubscriber) {

        this.messagingTemplate = messagingTemplate;
        messageSubscriber.subscribe(UserDataChangedMessage.TOPIC, this::deliverToConnectedBrowsers);
    }

    /**
     * Sends the refresh signal to the account's browsers connected to this pod.
     *
     * @param message the announcement, from whichever replica committed the change
     */
    void deliverToConnectedBrowsers(final UserDataChangedMessage message) {
        messagingTemplate.convertAndSendToUser(
                message.ownerId().toString(),
                REFRESH_DESTINATION,
                new DataChangedNotificationDto(message.changedAt(), message.originDeviceId()));

        LOGGER.debug("Passed a change to account {} to its browsers on this pod", message.ownerId());
    }
}
