package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.common.exceptions.WebhookEventInFlightException;
import com.kovospace.newtablinks.entitlement.models.PaymentProvider;
import com.kovospace.newtablinks.payment.services.PaymentWebhookClaimStore.ClaimState;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Decides whether this delivery of a webhook event is the one that processes it.
 *
 * <p>Exactly one delivery of an event may apply it. The first to insert the claim wins; every
 * later one finds the claim and is either a duplicate (the claim finished), early (the claim is
 * in flight - come back later), or the rescuer of a claim whose process died half way.</p>
 *
 * <p>The rescue exists because the claim commits before the entitlement does. A pod killed
 * between the two would otherwise leave an event claimed forever and never applied - a customer
 * who paid and got nothing, with every redelivery politely acknowledged as a duplicate.</p>
 *
 * @since 0.0.9
 */
@Service
public class PaymentWebhookClaimService {

    /**
     * How long an unfinished claim is trusted to belong to a live process.
     *
     * <p>Processing is one short database transaction, so two minutes is generous. Creem
     * redelivers after 30 seconds, 5 minutes, 30 minutes and 6 hours: the 30-second retry of a
     * slow delivery is told to come back, and the 5-minute one rescues a dead one.</p>
     */
    static final Duration ABANDONED_CLAIM_AGE = Duration.ofMinutes(2);

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentWebhookClaimService.class);

    private final PaymentWebhookClaimStore claimStore;

    /**
     * Creates the service.
     *
     * @param claimStore the claim table's transactional primitives
     */
    public PaymentWebhookClaimService(final PaymentWebhookClaimStore claimStore) {
        this.claimStore = claimStore;
    }

    /**
     * Claims an event for this delivery, if it is this delivery's to process.
     *
     * @param provider        provider that delivered the event
     * @param providerEventId the provider's identifier of the event
     * @param eventType       the provider's name for the kind of event
     * @return {@code true} when this delivery must process the event, {@code false} when it was
     *         already processed and this delivery is a duplicate
     * @throws WebhookEventInFlightException when another delivery is processing it right now
     */
    public boolean claimForProcessing(
            final PaymentProvider provider,
            final String providerEventId,
            final String eventType) {

        if (tryInsertClaim(provider, providerEventId, eventType)) {
            return true;
        }
        final Optional<ClaimState> existingClaim =
                claimStore.findClaim(provider, providerEventId);
        if (existingClaim.isPresent() && existingClaim.get().finished()) {
            return false;
        }
        if (existingClaim.isPresent() && !isAbandoned(existingClaim.get())) {
            throw new WebhookEventInFlightException(providerEventId);
        }
        return rescueClaim(provider, providerEventId, eventType);
    }

    /**
     * Takes over a claim nobody is working on - abandoned, or released since the insert failed.
     *
     * @param provider        provider that delivered the event
     * @param providerEventId the provider's identifier of the event
     * @param eventType       the provider's name for the kind of event
     * @return {@code true} once this delivery holds the claim
     * @throws WebhookEventInFlightException when another delivery took it over first
     */
    private boolean rescueClaim(
            final PaymentProvider provider,
            final String providerEventId,
            final String eventType) {

        if (claimStore.deleteAbandonedClaim(
                provider, providerEventId, Instant.now().minus(ABANDONED_CLAIM_AGE))) {
            LOGGER.warn("Took over the abandoned claim of {} webhook event {}",
                    provider, providerEventId);
        }
        if (tryInsertClaim(provider, providerEventId, eventType)) {
            return true;
        }
        throw new WebhookEventInFlightException(providerEventId);
    }

    /**
     * Inserts the claim, reporting a duplicate as {@code false} rather than as an exception.
     *
     * @param provider        provider that delivered the event
     * @param providerEventId the provider's identifier of the event
     * @param eventType       the provider's name for the kind of event
     * @return {@code true} when the claim was inserted
     */
    private boolean tryInsertClaim(
            final PaymentProvider provider,
            final String providerEventId,
            final String eventType) {

        try {
            claimStore.insertClaim(provider, providerEventId, eventType);
            return true;
        } catch (final DataIntegrityViolationException alreadyClaimed) {
            return false;
        }
    }

    /**
     * Tells whether an unfinished claim has outlived any process that could still hold it.
     *
     * @param claim the claim
     * @return {@code true} when it is older than {@link #ABANDONED_CLAIM_AGE}
     */
    private static boolean isAbandoned(final ClaimState claim) {
        return claim.claimedAt().isBefore(Instant.now().minus(ABANDONED_CLAIM_AGE));
    }
}
