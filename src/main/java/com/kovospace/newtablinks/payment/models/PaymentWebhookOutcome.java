package com.kovospace.newtablinks.payment.models;

import com.kovospace.newtablinks.entitlement.models.EntitlementSignalOutcome;

/**
 * What processing one webhook event did, as recorded on its claim.
 *
 * <p>Stored by name and listed in {@code ck_payment_webhook_event_outcome} in the migrated
 * schema; a value added here needs a migration first.</p>
 *
 * @since 0.0.9
 */
public enum PaymentWebhookOutcome {

    /** The entitlement was created or changed. */
    APPLIED,

    /** Older than the newest event already applied, so refused. */
    IGNORED_STALE,

    /** About a purchase the entitlement does not rest on, so left alone. */
    IGNORED_UNRELATED,

    /** An event type this service does not act on, or a partial refund. */
    IGNORED_UNHANDLED_TYPE,

    /** No account could be found for it; an operator has to look. */
    UNATTRIBUTED;

    /**
     * Translates what the entitlement module reported.
     *
     * @param entitlementSignalOutcome the entitlement module's outcome
     * @return the matching webhook outcome
     */
    public static PaymentWebhookOutcome of(final EntitlementSignalOutcome entitlementSignalOutcome) {
        return switch (entitlementSignalOutcome) {
            case APPLIED -> APPLIED;
            case IGNORED_STALE -> IGNORED_STALE;
            case IGNORED_UNRELATED -> IGNORED_UNRELATED;
            case UNATTRIBUTED -> UNATTRIBUTED;
        };
    }
}
