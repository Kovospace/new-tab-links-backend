package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.entitlement.models.PaymentProvider;
import com.kovospace.newtablinks.payment.models.PaymentWebhookEventEntity;
import com.kovospace.newtablinks.payment.repositories.PaymentWebhookEventRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The claim table's primitives, each in a transaction of its own.
 *
 * <p>{@code REQUIRES_NEW} is the point of this class. On PostgreSQL a unique-constraint violation
 * aborts the transaction it happens in - every later statement fails until rollback - so the
 * duplicate insert that detects a replay cannot share a transaction with anything that has to
 * survive it. And an exists-check before the insert does not work at all: two concurrent
 * redeliveries both pass it. So the claim is its own short transaction, committed before the
 * entitlement is touched, and a duplicate fails there and nowhere else.</p>
 *
 * <p>Kept apart from {@link PaymentWebhookClaimService} because Spring applies
 * {@code @Transactional} through a proxy: a call from inside the same class would bypass it and
 * silently run without a transaction of its own.</p>
 *
 * @since 0.0.9
 */
@Service
public class PaymentWebhookClaimStore {

    private final PaymentWebhookEventRepository paymentWebhookEventRepository;

    /**
     * Creates the store.
     *
     * @param paymentWebhookEventRepository persistence access for claims
     */
    public PaymentWebhookClaimStore(
            final PaymentWebhookEventRepository paymentWebhookEventRepository) {

        this.paymentWebhookEventRepository = paymentWebhookEventRepository;
    }

    /**
     * Inserts a claim and commits it.
     *
     * @param provider        provider that delivered the event
     * @param providerEventId the provider's identifier of the event
     * @param eventType       the provider's name for the kind of event
     * @throws DataIntegrityViolationException when the event was claimed before
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertClaim(
            final PaymentProvider provider,
            final String providerEventId,
            final String eventType) {

        paymentWebhookEventRepository.saveAndFlush(
                new PaymentWebhookEventEntity(provider, providerEventId, eventType));
    }

    /**
     * Reads the state of an existing claim.
     *
     * @param provider        provider that delivered the event
     * @param providerEventId the provider's identifier of the event
     * @return the claim's state, or empty when there is none
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ClaimState> findClaim(
            final PaymentProvider provider,
            final String providerEventId) {

        return paymentWebhookEventRepository
                .findByPaymentProviderAndProviderEventId(provider, providerEventId)
                .map(claim -> new ClaimState(claim.isFinished(), claim.getCreatedAt()));
    }

    /**
     * Deletes a claim that was taken before the given moment and never finished.
     *
     * @param provider        provider that delivered the event
     * @param providerEventId the provider's identifier of the event
     * @param claimedBefore   only an older claim is deleted
     * @return {@code true} when a claim was deleted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteAbandonedClaim(
            final PaymentProvider provider,
            final String providerEventId,
            final Instant claimedBefore) {

        return paymentWebhookEventRepository.deleteUnfinishedClaimTakenBefore(
                provider, providerEventId, claimedBefore) > 0;
    }

    /**
     * Gives an unfinished claim back, so that the provider's next delivery processes the event.
     *
     * @param provider        provider that delivered the event
     * @param providerEventId the provider's identifier of the event
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(final PaymentProvider provider, final String providerEventId) {
        paymentWebhookEventRepository.deleteUnfinishedClaim(provider, providerEventId);
    }

    /**
     * What is known about an existing claim.
     *
     * @param finished  whether processing finished
     * @param claimedAt when the claim was taken
     */
    public record ClaimState(boolean finished, Instant claimedAt) {
    }
}
