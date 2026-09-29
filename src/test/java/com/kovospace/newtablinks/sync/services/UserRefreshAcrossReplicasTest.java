package com.kovospace.newtablinks.sync.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.kovospace.newtablinks.common.messaging.MessageHandlerRegistry;
import com.kovospace.newtablinks.common.messaging.postgres.NotifyingPostgres;
import com.kovospace.newtablinks.common.messaging.postgres.PostgresNotificationListener;
import com.kovospace.newtablinks.sync.dtos.DataChangedNotificationDto;
import com.kovospace.newtablinks.sync.events.UserDataChangedEvent;
import com.kovospace.newtablinks.sync.events.UserDataChangedMessage;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies that a change committed on one replica reaches a browser connected to another.
 *
 * <p>The defect this pins: the notifier sent straight to its own pod's simple broker, which only
 * knows the websocket sessions of that pod. With two replicas a browser connected to the other one
 * was never told, and nothing anywhere reported it. Each "pod" here has its own messaging template
 * - standing for its own set of websocket sessions - and they share nothing but the database.</p>
 *
 * @since 0.0.12
 */
class UserRefreshAcrossReplicasTest {

    private static final long DELIVERY_TIMEOUT_MILLIS = 10_000;

    private NotifyingPostgres database;

    @BeforeEach
    void startDatabase() {
        database = NotifyingPostgres.startOrSkip();
    }

    @AfterEach
    void stopDatabase() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    @DisplayName("a change committed on one pod is passed to the browsers connected to the other")
    void shouldReachBrowsersConnectedToAnotherReplica() {
        final SimpMessagingTemplate sessionsOnCommittingPod = mock(SimpMessagingTemplate.class);
        final SimpMessagingTemplate sessionsOnOtherPod = mock(SimpMessagingTemplate.class);
        final UserRefreshNotifier notifierOnCommittingPod = startPod(sessionsOnCommittingPod);
        startPod(sessionsOnOtherPod);
        final UUID ownerId = UUID.randomUUID();

        notifierOnCommittingPod.notifyUserOfCommittedChange(
                new UserDataChangedEvent(ownerId, "installation-1"));

        verify(sessionsOnOtherPod, timeout(DELIVERY_TIMEOUT_MILLIS)).convertAndSendToUser(
                eq(ownerId.toString()),
                eq(UserRefreshRelay.REFRESH_DESTINATION),
                any(DataChangedNotificationDto.class));
        verify(sessionsOnCommittingPod, timeout(DELIVERY_TIMEOUT_MILLIS)).convertAndSendToUser(
                eq(ownerId.toString()),
                eq(UserRefreshRelay.REFRESH_DESTINATION),
                any(DataChangedNotificationDto.class));
    }

    /**
     * Starts one pod's side of it: its sessions, its relay, its listener, and its notifier.
     *
     * @param connectedSessions that pod's websocket sessions
     * @return that pod's notifier, which the sync push would call after a commit
     */
    private UserRefreshNotifier startPod(final SimpMessagingTemplate connectedSessions) {
        final MessageHandlerRegistry registry = new MessageHandlerRegistry(JsonMapper.builder().build());
        new UserRefreshRelay(connectedSessions, registry);
        final PostgresNotificationListener listener = database.startReplica(registry);
        NotifyingPostgres.awaitListening(listener, UserDataChangedMessage.TOPIC.name());
        return new UserRefreshNotifier(database.publisher());
    }
}
