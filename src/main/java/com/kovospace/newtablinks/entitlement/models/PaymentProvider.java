package com.kovospace.newtablinks.entitlement.models;

/**
 * A payment provider whose signals this application accepts.
 *
 * <p>Stored by name in two CHECK constraints of the migrated schema
 * ({@code ck_user_entitlement_provider}, {@code ck_payment_webhook_event_provider}). Replacing the
 * provider means a new value here, a migration, and one adapter in the payment module - nothing
 * in the entitlement model itself changes.</p>
 *
 * @since 0.0.9
 */
public enum PaymentProvider {

    /** Creem, the merchant of record. */
    CREEM
}
