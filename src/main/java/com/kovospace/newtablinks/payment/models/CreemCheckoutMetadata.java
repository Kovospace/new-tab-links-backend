package com.kovospace.newtablinks.payment.models;

/**
 * The metadata keys this service writes into a Creem checkout and reads back from webhooks.
 *
 * <p>Metadata, not {@code request_id}, is what carries the account: Creem echoes
 * {@code request_id} only on checkout objects ({@code checkout.completed}, and the checkout inside
 * {@code refund.created}), whereas checkout metadata is copied onto the subscription and therefore
 * arrives on every {@code subscription.*} event, renewals a year later included.</p>
 *
 * @since 0.0.9
 */
public final class CreemCheckoutMetadata {

    /** Key under which the paying account's identifier travels. */
    public static final String ACCOUNT_ID_KEY = "newtablinks_account_id";

    /**
     * Prevents instantiation of this constant holder.
     */
    private CreemCheckoutMetadata() {
    }
}
