package com.kovospace.newtablinks.sync.exceptions;

import com.kovospace.newtablinks.sync.dtos.SyncRejectionReason;

/**
 * Thrown by an applier when one pushed operation cannot be carried out.
 *
 * <p>Never escapes
 * {@link com.kovospace.newtablinks.sync.services.SyncPushService}: it is caught there, turned
 * into an entry in the response's {@code rejected} list, and the batch carries on. There is
 * therefore no handler for it in
 * {@link com.kovospace.newtablinks.common.exceptions.GlobalExceptionHandler}, and adding one
 * would be a sign that something is throwing it from the wrong place.</p>
 *
 * <p>It is thrown <em>before</em> the operation writes anything, and never from inside another
 * service's transactional method. That matters more than it looks: an exception crossing a
 * {@code @Transactional} boundary marks the surrounding transaction rollback-only whatever the
 * caller does with it, so a rejection raised the wrong way would take the whole push down with
 * it. The appliers resolve parents through lookups that return an empty optional instead of
 * throwing, precisely so this exception can be raised on this side of that boundary.</p>
 *
 * @since 0.0.6
 */
public class SyncOperationRejectedException extends RuntimeException {

    private final transient SyncRejectionReason reason;

    /**
     * Creates the exception.
     *
     * @param reason why the operation cannot be carried out
     */
    public SyncOperationRejectedException(final SyncRejectionReason reason) {
        super("Synchronization operation rejected: " + reason);
        this.reason = reason;
    }

    /**
     * Returns why the operation was rejected, for the response body.
     *
     * @return the reason
     */
    public SyncRejectionReason getReason() {
        return reason;
    }
}
