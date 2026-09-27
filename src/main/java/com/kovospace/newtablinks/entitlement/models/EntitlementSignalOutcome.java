package com.kovospace.newtablinks.entitlement.models;

/**
 * What applying one {@link EntitlementSignal} did.
 *
 * @since 0.0.9
 */
public enum EntitlementSignalOutcome {

    /** The entitlement was created or changed. */
    APPLIED,

    /** Older than the newest event already applied to the entitlement, so refused. */
    IGNORED_STALE,

    /** Concerns a purchase other than the one the entitlement rests on, so left alone. */
    IGNORED_UNRELATED,

    /** No account could be found for it. Nothing changed, and an operator has to look. */
    UNATTRIBUTED
}
