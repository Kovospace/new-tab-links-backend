package com.kovospace.newtablinks.sync.events;

import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The one line a service calls after changing something a client synchronizes.
 *
 * <p>Exists so that services depend on a single, obviously named collaborator instead of on
 * {@link ApplicationEventPublisher} and an event type, and so the call reads as what it means.</p>
 *
 * @since 0.0.3
 */
@Component
public class UserDataChangePublisher {

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
     * Announces that a user's data has changed.
     *
     * <p>Safe to call inside a transaction: listeners run after it commits, so nobody is told to
     * re-read data that is not visible yet.</p>
     *
     * @param ownerId identifier of the user whose data changed
     */
    public void publishChangeFor(final UUID ownerId) {
        applicationEventPublisher.publishEvent(new UserDataChangedEvent(ownerId));
    }
}
