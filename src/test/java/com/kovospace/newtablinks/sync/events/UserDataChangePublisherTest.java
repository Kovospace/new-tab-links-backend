package com.kovospace.newtablinks.sync.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tests that one logical change produces one announcement, however many services touched it.
 *
 * @since 0.0.5
 */
class UserDataChangePublisherTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID ANOTHER_OWNER_ID = UUID.randomUUID();

    private final ApplicationEventPublisher applicationEventPublisher =
            mock(ApplicationEventPublisher.class);

    private final UserDataChangePublisher userDataChangePublisher =
            new UserDataChangePublisher(applicationEventPublisher);

    @AfterEach
    void endAnyTransactionStartedByATest() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            completeTransaction();
        }
    }

    /**
     * Starts a transaction, as far as the synchronization manager is concerned.
     */
    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    /**
     * Finishes it the way a real transaction manager does.
     *
     * <p>{@code clearSynchronization} alone would not do: it discards the registered callbacks
     * without running them, so the publisher's own cleanup would never fire and the set of
     * already-announced accounts would stay bound to this thread and silence the next test.</p>
     */
    private void completeTransaction() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("announces a change made outside any transaction")
    void announcesWithoutATransaction() {

        userDataChangePublisher.publishChangeFor(OWNER_ID);

        verify(applicationEventPublisher).publishEvent(new UserDataChangedEvent(OWNER_ID, null));
    }

    @Test
    @DisplayName("carries the device that caused the change, so it can ignore its own echo")
    void carriesTheOriginDevice() {

        userDataChangePublisher.publishChangeFor(OWNER_ID, "a-device");

        verify(applicationEventPublisher)
                .publishEvent(new UserDataChangedEvent(OWNER_ID, "a-device"));
    }

    @Test
    @DisplayName("announces once per account however many times one transaction changes it")
    void coalescesRepeatedAnnouncementsWithinATransaction() {

        beginTransaction();

        // What a push of many operations does: every module's service announces its own change.
        userDataChangePublisher.publishChangeFor(OWNER_ID, "a-device");
        userDataChangePublisher.publishChangeFor(OWNER_ID);
        userDataChangePublisher.publishChangeFor(OWNER_ID);

        verify(applicationEventPublisher, times(1)).publishEvent(new UserDataChangedEvent(OWNER_ID, "a-device"));
    }

    @Test
    @DisplayName("keeps the device named by the first announcement of the transaction")
    void theFirstAnnouncementDecidesTheOrigin() {

        beginTransaction();

        userDataChangePublisher.publishChangeFor(OWNER_ID, "a-device");
        userDataChangePublisher.publishChangeFor(OWNER_ID);

        final ArgumentCaptor<UserDataChangedEvent> published =
                ArgumentCaptor.forClass(UserDataChangedEvent.class);
        verify(applicationEventPublisher, times(1)).publishEvent(published.capture());

        assertThat(published.getValue().originDeviceId()).isEqualTo("a-device");
    }

    @Test
    @DisplayName("does not let one account's announcement silence another's")
    void coalescesPerAccountRatherThanPerTransaction() {

        beginTransaction();

        userDataChangePublisher.publishChangeFor(OWNER_ID);
        userDataChangePublisher.publishChangeFor(ANOTHER_OWNER_ID);

        verify(applicationEventPublisher).publishEvent(new UserDataChangedEvent(OWNER_ID, null));
        verify(applicationEventPublisher)
                .publishEvent(new UserDataChangedEvent(ANOTHER_OWNER_ID, null));
    }

    @Test
    @DisplayName("announces again in the next transaction")
    void startsAfreshInTheFollowingTransaction() {

        beginTransaction();
        userDataChangePublisher.publishChangeFor(OWNER_ID);
        completeTransaction();

        beginTransaction();
        userDataChangePublisher.publishChangeFor(OWNER_ID);

        verify(applicationEventPublisher, times(2))
                .publishEvent(new UserDataChangedEvent(OWNER_ID, null));
    }
}
