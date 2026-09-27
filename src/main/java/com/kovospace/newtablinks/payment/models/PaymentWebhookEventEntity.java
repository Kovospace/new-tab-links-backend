package com.kovospace.newtablinks.payment.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import com.kovospace.newtablinks.entitlement.models.PaymentProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * One webhook event a payment provider delivered, claimed before it is applied.
 *
 * <p>The row is the replay protection. Its unique index on provider and event identifier
 * ({@code uk_payment_webhook_event_provider_event}) exists only in the migrated schema - {@code
 * validate} does not check it - and it is what turns a second delivery of the same event into a
 * no-op. {@link #getCreatedAt()} is when the claim was taken; the outcome stays {@code null} until
 * processing finished.</p>
 *
 * @since 0.0.9
 */
@Entity
@Table(name = "payment_webhook_event")
public class PaymentWebhookEventEntity extends AbstractAuditableEntity {

    /** Provider that delivered the event. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", nullable = false, length = 20, updatable = false)
    private PaymentProvider paymentProvider;

    /** The provider's identifier of the event, identical on every redelivery. */
    @Column(name = "provider_event_id", nullable = false, length = 100, updatable = false)
    private String providerEventId;

    /** The provider's name for the kind of event, kept for diagnosis. */
    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private String eventType;

    /** What processing did; {@code null} while the claim is in flight. */
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 40)
    private PaymentWebhookOutcome outcome;

    /** When processing finished; {@code null} while the claim is in flight. */
    @Column(name = "processed_at")
    private Instant processedAt;

    /**
     * Required by JPA.
     */
    protected PaymentWebhookEventEntity() {
    }

    /**
     * Claims an event for processing.
     *
     * @param paymentProvider provider that delivered it
     * @param providerEventId the provider's identifier of the event
     * @param eventType       the provider's name for the kind of event
     */
    public PaymentWebhookEventEntity(
            final PaymentProvider paymentProvider,
            final String providerEventId,
            final String eventType) {

        this.paymentProvider = Objects.requireNonNull(paymentProvider, "paymentProvider");
        this.providerEventId = Objects.requireNonNull(providerEventId, "providerEventId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
    }

    /**
     * Marks the claim finished.
     *
     * @param processingOutcome what processing did
     * @param finishedAt        when it finished
     */
    public void recordOutcome(final PaymentWebhookOutcome processingOutcome, final Instant finishedAt) {
        this.outcome = Objects.requireNonNull(processingOutcome, "processingOutcome");
        this.processedAt = Objects.requireNonNull(finishedAt, "finishedAt");
    }

    /**
     * Tells whether processing of this event has finished.
     *
     * @return {@code true} once an outcome is recorded
     */
    public boolean isFinished() {
        return outcome != null;
    }

    /** @return the provider that delivered the event */
    public PaymentProvider getPaymentProvider() {
        return paymentProvider;
    }

    /** @return the provider's identifier of the event */
    public String getProviderEventId() {
        return providerEventId;
    }

    /** @return the provider's name for the kind of event */
    public String getEventType() {
        return eventType;
    }

    /** @return what processing did, or {@code null} while in flight */
    public PaymentWebhookOutcome getOutcome() {
        return outcome;
    }

    /** @return when processing finished, or {@code null} while in flight */
    public Instant getProcessedAt() {
        return processedAt;
    }
}
