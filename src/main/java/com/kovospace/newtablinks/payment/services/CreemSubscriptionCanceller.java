package com.kovospace.newtablinks.payment.services;

import static com.kovospace.newtablinks.payment.utils.WebhookPayloadFields.textOrNull;

import com.kovospace.newtablinks.common.exceptions.PaymentProviderRequestFailedException;
import com.kovospace.newtablinks.payment.config.CreemProperties;
import com.kovospace.newtablinks.payment.config.CreemRestClientConfiguration;
import com.kovospace.newtablinks.payment.models.SubscriptionCancellationResult;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Creem's implementation of {@link PaymentSubscriptionCanceller}.
 *
 * <p>{@code POST /v1/subscriptions/{id}/cancel} with {@code {"mode": "immediate"}} - Creem's
 * {@code CancelSubscriptionRequestEntity}, whose {@code mode} is "immediate or scheduled, default
 * can be configured in the store billing settings", so it is always sent rather than left to the
 * dashboard. {@code onExecute} applies only to scheduled mode and is not sent.</p>
 *
 * <p>Creem documents a 200 carrying the subscription, and 400, 401 and 404 without saying which
 * one an already cancelled subscription gets. So a refusal is not interpreted from its body:
 * the subscription is read back with {@code GET /v1/subscriptions?subscription_id=} and its
 * {@code status} decides. {@code canceled} and {@code expired} are over; {@code scheduled_cancel}
 * ends at period end without another charge, so it too counts as done when Creem will not cancel
 * it outright. Anything else stays a failure, and the caller retries.</p>
 *
 * @since 0.0.9
 */
@Service
public class CreemSubscriptionCanceller implements PaymentSubscriptionCanceller {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreemSubscriptionCanceller.class);

    /** Creem's cancellation endpoint, relative to the API host. */
    static final String CANCEL_PATH = "/v1/subscriptions/{subscriptionId}/cancel";

    /** Creem's retrieval endpoint, relative to the API host. */
    static final String RETRIEVE_PATH = "/v1/subscriptions?subscription_id={subscriptionId}";

    /** The body asking for an immediate cancellation, never one at period end. */
    private static final Map<String, String> IMMEDIATE_CANCELLATION = Map.of("mode", "immediate");

    /** Creem subscription statuses under which it will never charge again. */
    private static final Set<String> STATUSES_THAT_NEVER_CHARGE_AGAIN =
            Set.of("canceled", "expired", "scheduled_cancel");

    private final CreemProperties creemProperties;
    private final RestClient creemRestClient;

    /**
     * Creates the canceller.
     *
     * @param creemProperties tells whether an API key is configured
     * @param creemRestClient client pointed at the key's host, carrying the key
     */
    public CreemSubscriptionCanceller(
            final CreemProperties creemProperties,
            @Qualifier(CreemRestClientConfiguration.CREEM_REST_CLIENT)
            final RestClient creemRestClient) {

        this.creemProperties = creemProperties;
        this.creemRestClient = creemRestClient;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEnabled() {
        return creemProperties.isApiConfigured();
    }

    /** {@inheritDoc} */
    @Override
    public SubscriptionCancellationResult cancelImmediately(final String subscriptionId) {
        try {
            final JsonNode response = creemRestClient.post()
                    .uri(CANCEL_PATH, subscriptionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(IMMEDIATE_CANCELLATION)
                    .retrieve()
                    .body(JsonNode.class);
            LOGGER.info("Creem cancelled subscription {}; it is now {}",
                    subscriptionId, response == null ? null : textOrNull(response, "status"));
            return SubscriptionCancellationResult.CANCELLED;
        } catch (final HttpClientErrorException refused) {
            return confirmNoLongerRenewing(subscriptionId, refused);
        } catch (final RestClientException failed) {
            throw new PaymentProviderRequestFailedException(
                    "Creem could not be asked to cancel subscription " + subscriptionId, failed);
        }
    }

    /**
     * Decides whether a refused cancellation is in fact done, by reading the subscription back.
     *
     * @param subscriptionId the subscription Creem refused to cancel
     * @param refusal        Creem's refusal
     * @return {@link SubscriptionCancellationResult#ALREADY_NOT_RENEWING} when it will not charge
     *         again
     * @throws PaymentProviderRequestFailedException when it is still live, or cannot be read
     */
    private SubscriptionCancellationResult confirmNoLongerRenewing(
            final String subscriptionId,
            final HttpClientErrorException refusal) {

        LOGGER.info("Creem refused to cancel subscription {} with {}: {}; reading it back",
                subscriptionId, refusal.getStatusCode(), refusal.getResponseBodyAsString());
        final String status = retrieveStatus(subscriptionId, refusal);
        if (status != null && STATUSES_THAT_NEVER_CHARGE_AGAIN.contains(status)) {
            LOGGER.info("Subscription {} is already {} at Creem; nothing left to cancel",
                    subscriptionId, status);
            return SubscriptionCancellationResult.ALREADY_NOT_RENEWING;
        }
        throw new PaymentProviderRequestFailedException("Creem refused to cancel subscription "
                + subscriptionId + " with " + refusal.getStatusCode() + ", and it is still "
                + status, refusal);
    }

    /**
     * Reads a subscription's status from Creem.
     *
     * @param subscriptionId the subscription
     * @param refusal        the refusal that prompted the read, kept as the cause on failure
     * @return Creem's status, or {@code null} when the response carries none
     * @throws PaymentProviderRequestFailedException when the subscription cannot be read
     */
    private String retrieveStatus(
            final String subscriptionId,
            final HttpClientErrorException refusal) {

        try {
            final JsonNode subscription = creemRestClient.get()
                    .uri(RETRIEVE_PATH, subscriptionId)
                    .retrieve()
                    .body(JsonNode.class);
            return subscription == null ? null : textOrNull(subscription, "status");
        } catch (final RestClientException unreadable) {
            final PaymentProviderRequestFailedException failure =
                    new PaymentProviderRequestFailedException("Creem refused to cancel "
                            + "subscription " + subscriptionId + " with "
                            + refusal.getStatusCode() + " and it could not be read back",
                            refusal);
            failure.addSuppressed(unreadable);
            throw failure;
        }
    }
}
