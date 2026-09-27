package com.kovospace.newtablinks.entitlement.models;

/**
 * Where an entitlement stands in the payment provider's lifecycle.
 *
 * <p>Stored by name and listed in the CHECK constraint {@code ck_user_entitlement_status} of the
 * migrated schema; see {@link EntitlementSource} for why that matters.</p>
 *
 * @since 0.0.9
 */
public enum EntitlementStatus {

    /** Paid and in good standing. */
    ACTIVE(true),

    /**
     * A renewal failed and the provider is retrying it.
     *
     * <p>Marks, never revokes: what was already paid for stays paid for until the entitlement's
     * paid-until instant.</p>
     */
    PAST_DUE(true),

    /** Cancelled at the end of the period; runs out at the paid-until instant. */
    SCHEDULED_CANCEL(true),

    /**
     * The subscription has ended. Grants nothing from that moment on.
     *
     * <p>Not "cancelled but paid up": that is {@link #SCHEDULED_CANCEL}. The provider sends
     * {@code subscription.canceled} when the subscription is over - cancelled immediately, or a
     * scheduled cancellation reaching its period end - and its own reference handling revokes
     * access on it. Honouring a paid period here once kept a cancelled buyer premium for up to a
     * year after they had left.</p>
     */
    CANCELED(false),

    /** The period ended without a new payment. */
    EXPIRED(false),

    /** The money went back to the customer. Grants nothing from that moment on. */
    REFUNDED(false);

    private final boolean honoursPaidPeriod;

    /**
     * Declares a status.
     *
     * @param honoursPaidPeriod whether an entitlement in this status still counts until its
     *                          paid-until instant
     */
    EntitlementStatus(final boolean honoursPaidPeriod) {
        this.honoursPaidPeriod = honoursPaidPeriod;
    }

    /**
     * Tells whether an entitlement in this status still counts until its paid-until instant.
     *
     * @return {@code false} for the statuses that end an entitlement immediately
     */
    public boolean honoursPaidPeriod() {
        return honoursPaidPeriod;
    }
}
