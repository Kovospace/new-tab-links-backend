package com.kovospace.newtablinks.sync.events;

import java.util.UUID;

/**
 * Published when something a client synchronizes has changed for one account.
 *
 * @param ownerId        identifier of the user whose data changed
 * @param originDeviceId opaque identifier of the device that caused the change, null when the
 *                       change came from somewhere that does not name a device - the website, or
 *                       any of the ordinary CRUD endpoints
 * @since 0.0.3
 */
public record UserDataChangedEvent(UUID ownerId, String originDeviceId) {
}
