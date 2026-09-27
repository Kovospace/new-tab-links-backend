package com.kovospace.newtablinks.payment.models;

/**
 * Where an account's pro plan stands, as the website shows it.
 *
 * <p>Coarser than the stored lifecycle: a subscription cancelled at the period end and one
 * cancelled outright both read {@link #CANCELLED}. Whether either still makes the account pro is
 * not this value's job - that is {@code premium} on the account, judged by the server.</p>
 *
 * @since 0.0.9
 */
public enum SubscriptionState {

    /** The account has never held a plan. */
    NONE,

    /** Paid and in good standing. */
    ACTIVE,

    /** A renewal failed and the provider is retrying it; what was paid for still counts. */
    PAST_DUE,

    /** Cancelled; the period already paid for still counts until it ends. */
    CANCELLED,

    /** The period ended without a new payment. */
    EXPIRED,

    /** The money went back to the customer. */
    REFUNDED
}
