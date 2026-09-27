package com.kovospace.newtablinks.entitlement.models;

/**
 * Where an account's pro entitlement came from.
 *
 * <p>Stored by name, and the names are listed in a CHECK constraint in the migrated schema
 * ({@code ck_user_entitlement_source}); a value added here needs a migration there first, or the
 * insert fails at runtime while {@code ddl-auto=validate} reports nothing at startup.</p>
 *
 * @since 0.0.9
 */
public enum EntitlementSource {

    /** A one-time purchase that never runs out. */
    LIFETIME,

    /** A recurring payment, good until the end of the period paid for. */
    SUBSCRIPTION,

    /**
     * Given by the operator without any payment; written only by {@code EntitlementGrantService}.
     */
    GRANT
}
