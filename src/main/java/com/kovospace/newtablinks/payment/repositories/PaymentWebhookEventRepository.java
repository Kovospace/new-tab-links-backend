package com.kovospace.newtablinks.payment.repositories;

import com.kovospace.newtablinks.entitlement.models.PaymentProvider;
import com.kovospace.newtablinks.payment.models.PaymentWebhookEventEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link PaymentWebhookEventEntity}.
 *
 * @since 0.0.9
 */
@Repository
public interface PaymentWebhookEventRepository
        extends JpaRepository<PaymentWebhookEventEntity, UUID> {

    /**
     * Finds the claim of one event.
     *
     * @param paymentProvider provider that delivered it
     * @param providerEventId the provider's identifier of the event
     * @return the claim, or empty when the event was never claimed
     */
    Optional<PaymentWebhookEventEntity> findByPaymentProviderAndProviderEventId(
            PaymentProvider paymentProvider, String providerEventId);

    /**
     * Deletes the claim of one event if processing never finished and it was taken before the
     * given moment.
     *
     * @param paymentProvider provider that delivered it
     * @param providerEventId the provider's identifier of the event
     * @param claimedBefore   only a claim older than this is deleted
     * @return how many rows were deleted, zero or one
     */
    @Modifying
    @Query("delete from PaymentWebhookEventEntity event "
            + "where event.paymentProvider = :paymentProvider "
            + "and event.providerEventId = :providerEventId "
            + "and event.outcome is null and event.createdAt < :claimedBefore")
    int deleteUnfinishedClaimTakenBefore(
            @Param("paymentProvider") PaymentProvider paymentProvider,
            @Param("providerEventId") String providerEventId,
            @Param("claimedBefore") Instant claimedBefore);

    /**
     * Deletes the claim of one event if processing never finished.
     *
     * @param paymentProvider provider that delivered it
     * @param providerEventId the provider's identifier of the event
     * @return how many rows were deleted, zero or one
     */
    @Modifying
    @Query("delete from PaymentWebhookEventEntity event "
            + "where event.paymentProvider = :paymentProvider "
            + "and event.providerEventId = :providerEventId "
            + "and event.outcome is null")
    int deleteUnfinishedClaim(
            @Param("paymentProvider") PaymentProvider paymentProvider,
            @Param("providerEventId") String providerEventId);
}
