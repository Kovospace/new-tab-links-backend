package com.kovospace.newtablinks.sync.events;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The one line a service calls after changing something a client synchronizes.
 *
 * <p>Exists so that services depend on a single, obviously named collaborator instead of on
 * {@link ApplicationEventPublisher} and an event type, and so the call reads as what it means.</p>
 *
 * <p><strong>At most one announcement per account per transaction.</strong> A single mutation
 * publishes once and behaves exactly as it always has, but a synchronization push applies
 * hundreds of operations in one transaction and each one calls its module's service. Without
 * coalescing that would send the user's other browsers hundreds of identical "you are out of
 * date" messages for one logical change, every one of which would provoke another snapshot
 * fetch.</p>
 *
 * @since 0.0.3
 */
@Component
public class UserDataChangePublisher {

    /**
     * Key under which the accounts already announced in the current transaction are held.
     */
    private static final String ANNOUNCED_ACCOUNTS_RESOURCE_KEY =
            UserDataChangePublisher.class.getName() + ".announcedAccounts";

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Creates the publisher.
     *
     * @param applicationEventPublisher Spring's event publisher
     */
    public UserDataChangePublisher(final ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Announces that a user's data has changed, without naming what caused it.
     *
     * <p>Safe to call inside a transaction: listeners run after it commits, so nobody is told to
     * re-read data that is not visible yet.</p>
     *
     * @param ownerId identifier of the user whose data changed
     */
    public void publishChangeFor(final UUID ownerId) {
        publishChangeFor(ownerId, null);
    }

    /**
     * Announces that a user's data has changed, naming the device that caused it.
     *
     * <p>The device travels on to the client as the notification's origin, so a browser can
     * recognise the echo of its own push and decline to re-fetch what it has just sent. A caller
     * that has no device to name uses {@link #publishChangeFor(UUID)} instead.</p>
     *
     * <p>Because only the first announcement per account survives a transaction, a caller that
     * wants its device named must announce <em>before</em> the work that will announce anonymously
     * on its behalf - which is what {@code SyncPushService} does.</p>
     *
     * @param ownerId        identifier of the user whose data changed
     * @param originDeviceId opaque identifier of the device that caused the change, may be null
     */
    public void publishChangeFor(final UUID ownerId, final String originDeviceId) {
        if (!isFirstAnnouncementInTransaction(ownerId)) {
            return;
        }
        applicationEventPublisher.publishEvent(new UserDataChangedEvent(ownerId, originDeviceId));
    }

    /**
     * Records the account as announced, and says whether it had not been announced already.
     *
     * <p>With no transaction in progress there is nothing to coalesce within, so every call is
     * treated as the first. That keeps the publisher usable outside a transaction rather than
     * silently swallowing the announcement.</p>
     *
     * @param ownerId identifier of the user whose data changed
     * @return true when this is the first announcement for that account in this transaction
     */
    private boolean isFirstAnnouncementInTransaction(final UUID ownerId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return true;
        }
        return announcedAccountsOfCurrentTransaction().add(ownerId);
    }

    /**
     * The set of accounts announced so far in this transaction, created on first use.
     *
     * <p>The set is unbound when the transaction finishes, however it finishes. Leaving it bound
     * would leak it into whatever the thread does next, and a pooled request thread does a great
     * deal next.</p>
     *
     * @return the mutable set held for the duration of the current transaction
     */
    @SuppressWarnings("unchecked")
    private Set<UUID> announcedAccountsOfCurrentTransaction() {
        final Object bound =
                TransactionSynchronizationManager.getResource(ANNOUNCED_ACCOUNTS_RESOURCE_KEY);

        if (bound != null) {
            return (Set<UUID>) bound;
        }
        final Set<UUID> announcedAccounts = new HashSet<>();
        TransactionSynchronizationManager.bindResource(
                ANNOUNCED_ACCOUNTS_RESOURCE_KEY, announcedAccounts);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(final int status) {
                TransactionSynchronizationManager
                        .unbindResourceIfPossible(ANNOUNCED_ACCOUNTS_RESOURCE_KEY);
            }
        });
        return announcedAccounts;
    }
}
