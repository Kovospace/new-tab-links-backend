package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.entitlement.services.EntitlementSignalService;
import com.kovospace.newtablinks.payment.dtos.PaymentWebhookReceiptDto;
import com.kovospace.newtablinks.payment.models.InterpretedPaymentWebhook;
import com.kovospace.newtablinks.payment.models.PaymentWebhookOutcome;
import com.kovospace.newtablinks.payment.repositories.PaymentWebhookEventRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Processes one webhook delivery: verify, claim, apply, record.
 *
 * <p>The order is what makes it safe:</p>
 * <ol>
 *   <li>The provider's adapter verifies the signature before anything else looks at the body.</li>
 *   <li>The event is claimed in a transaction of its own ({@link PaymentWebhookClaimService}). A
 *       replay stops here, having changed nothing.</li>
 *   <li>The entitlement change and the claim's outcome are written in one transaction, so an
 *       event is never recorded as applied without having been applied.</li>
 *   <li>If that transaction fails, the claim is released and the failure answered with an error,
 *       so the provider redelivers and the next attempt starts clean.</li>
 * </ol>
 *
 * <p>Nothing here is transactional as a whole, deliberately: step 2 has to commit before step 3
 * starts.</p>
 *
 * @since 0.0.9
 */
@Service
public class PaymentWebhookProcessingService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PaymentWebhookProcessingService.class);

    private final PaymentWebhookInterpreter paymentWebhookInterpreter;
    private final PaymentWebhookClaimService claimService;
    private final PaymentWebhookClaimStore claimStore;
    private final EntitlementSignalService entitlementSignalService;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * Creates the service.
     *
     * @param paymentWebhookInterpreter     the provider adapter: verifies and translates
     * @param claimService                  decides whether this delivery processes the event
     * @param claimStore                    releases a claim whose processing failed
     * @param entitlementSignalService      applies the translated signal
     * @param paymentWebhookEventRepository records the outcome on the claim
     * @param transactionManager            runs the apply-and-record transaction
     */
    public PaymentWebhookProcessingService(
            final PaymentWebhookInterpreter paymentWebhookInterpreter,
            final PaymentWebhookClaimService claimService,
            final PaymentWebhookClaimStore claimStore,
            final EntitlementSignalService entitlementSignalService,
            final PaymentWebhookEventRepository paymentWebhookEventRepository,
            final PlatformTransactionManager transactionManager) {

        this.paymentWebhookInterpreter = paymentWebhookInterpreter;
        this.claimService = claimService;
        this.claimStore = claimStore;
        this.entitlementSignalService = entitlementSignalService;
        this.paymentWebhookEventRepository = paymentWebhookEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Processes one delivery.
     *
     * @param requestBody        the body exactly as received
     * @param presentedSignature the signature header, possibly {@code null}
     * @return what happened, for the acknowledgement
     */
    public PaymentWebhookReceiptDto processDelivery(
            final byte[] requestBody,
            final String presentedSignature) {

        final InterpretedPaymentWebhook webhook =
                paymentWebhookInterpreter.verifyAndInterpret(requestBody, presentedSignature);

        if (!claimService.claimForProcessing(
                webhook.provider(), webhook.providerEventId(), webhook.eventType())) {
            LOGGER.info("Acknowledged a repeated delivery of {} event {} ({})",
                    webhook.provider(), webhook.providerEventId(), webhook.eventType());
            return PaymentWebhookReceiptDto.duplicate(webhook.providerEventId());
        }

        final PaymentWebhookOutcome outcome = applyReleasingClaimOnFailure(webhook);
        LOGGER.info("Processed {} event {} ({}): {}", webhook.provider(),
                webhook.providerEventId(), webhook.eventType(), outcome);
        return PaymentWebhookReceiptDto.processed(webhook.providerEventId(), outcome);
    }

    /**
     * Applies the event, and gives the claim back if that fails.
     *
     * @param webhook the claimed event
     * @return what applying it did
     */
    private PaymentWebhookOutcome applyReleasingClaimOnFailure(
            final InterpretedPaymentWebhook webhook) {

        try {
            return transactionTemplate.execute(transactionStatus -> applyAndRecord(webhook));
        } catch (final RuntimeException processingFailure) {
            try {
                claimStore.releaseClaim(webhook.provider(), webhook.providerEventId());
            } catch (final RuntimeException releaseFailure) {
                // The claim now waits out ABANDONED_CLAIM_AGE before a redelivery can rescue it.
                processingFailure.addSuppressed(releaseFailure);
            }
            throw processingFailure;
        }
    }

    /**
     * Applies the event's signal and records the outcome on its claim, in the caller's
     * transaction.
     *
     * @param webhook the claimed event
     * @return what applying it did
     */
    private PaymentWebhookOutcome applyAndRecord(final InterpretedPaymentWebhook webhook) {
        final PaymentWebhookOutcome outcome = webhook.signal()
                .map(entitlementSignalService::applySignal)
                .map(PaymentWebhookOutcome::of)
                .orElse(PaymentWebhookOutcome.IGNORED_UNHANDLED_TYPE);

        paymentWebhookEventRepository
                .findByPaymentProviderAndProviderEventId(
                        webhook.provider(), webhook.providerEventId())
                .orElseThrow(() -> new IllegalStateException(
                        "The claim of event " + webhook.providerEventId() + " disappeared"))
                .recordOutcome(outcome, Instant.now());
        return outcome;
    }
}
