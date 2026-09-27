package com.kovospace.newtablinks.entitlement.models;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One fact a payment provider reported about somebody's entitlement.
 *
 * <p>The provider is a dumb signal: it says "this account paid, until X" or "that payment was
 * refunded", and the entitlement module decides what that means for the account. Everything
 * provider-specific - the wire format, the signature, the event names - stays in the adapter that
 * builds this record.</p>
 *
 * @param kind                what happened
 * @param provider            who reported it
 * @param occurredAt          the provider's own timestamp of the event; what out-of-order
 *                            delivery is judged by
 * @param attributedAccountId the account the payment was made for, as carried through checkout,
 *                            or {@code null} when the event did not carry it
 * @param references          the provider's identifiers for the purchase
 * @param paidUntil           end of the period paid for, or {@code null} when not reported
 * @param chargedAmount       what the customer actually paid, or {@code null} when not reported
 * @since 0.0.9
 */
public record EntitlementSignal(
        EntitlementSignalKind kind,
        PaymentProvider provider,
        Instant occurredAt,
        UUID attributedAccountId,
        ProviderPurchaseReferences references,
        Instant paidUntil,
        ChargedAmount chargedAmount) {

    /**
     * Rejects a signal missing what every signal must have.
     *
     * @throws NullPointerException when the kind, provider, timestamp or references are missing
     */
    public EntitlementSignal {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(references, "references");
    }
}
