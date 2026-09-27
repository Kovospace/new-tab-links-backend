package com.kovospace.newtablinks.payment.models;

import com.kovospace.newtablinks.entitlement.models.EntitlementSignal;
import com.kovospace.newtablinks.entitlement.models.PaymentProvider;
import java.util.Objects;
import java.util.Optional;

/**
 * A webhook delivery whose signature has been verified, translated out of the provider's format.
 *
 * @param provider           provider that delivered it
 * @param providerEventId    the provider's identifier of the event, the replay key
 * @param eventType          the provider's name for the kind of event
 * @param entitlementSignal  what it means for an entitlement, or {@code null} when the event is
 *                           not one this service acts on
 * @since 0.0.9
 */
public record InterpretedPaymentWebhook(
        PaymentProvider provider,
        String providerEventId,
        String eventType,
        EntitlementSignal entitlementSignal) {

    /**
     * Rejects an interpretation missing what every delivery must have.
     *
     * @throws NullPointerException when the provider, event identifier or type is missing
     */
    public InterpretedPaymentWebhook {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerEventId, "providerEventId");
        Objects.requireNonNull(eventType, "eventType");
    }

    /**
     * Returns what the event means for an entitlement.
     *
     * @return the signal, or empty when the event is not one this service acts on
     */
    public Optional<EntitlementSignal> signal() {
        return Optional.ofNullable(entitlementSignal);
    }
}
