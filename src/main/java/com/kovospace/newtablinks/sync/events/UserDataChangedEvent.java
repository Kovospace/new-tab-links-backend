package com.kovospace.newtablinks.sync.events;

import java.util.UUID;

/**
 * Raised when a user's stored data has been changed by something.
 *
 * <p>A domain fact rather than an HTTP one, so services raise it and anything interested - today
 * only the websocket notifier - reacts. The domain layer therefore knows nothing about
 * websockets, and a future non-HTTP writer gets notifications for free.</p>
 *
 * @param ownerId identifier of the user whose data changed
 * @since 0.0.3
 */
public record UserDataChangedEvent(UUID ownerId) {
}
