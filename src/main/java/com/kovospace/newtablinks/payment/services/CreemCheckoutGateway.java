package com.kovospace.newtablinks.payment.services;

import static com.kovospace.newtablinks.payment.utils.WebhookPayloadFields.textOrNull;

import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.common.exceptions.PaymentProviderNotConfiguredException;
import com.kovospace.newtablinks.common.exceptions.PaymentProviderRequestFailedException;
import com.kovospace.newtablinks.payment.config.CreemProperties;
import com.kovospace.newtablinks.payment.config.CreemRestClientConfiguration;
import com.kovospace.newtablinks.payment.models.CheckoutRequest;
import com.kovospace.newtablinks.payment.models.CheckoutSession;
import com.kovospace.newtablinks.payment.models.CreemCheckoutMetadata;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Creem's implementation of {@link PaymentCheckoutGateway}: {@code POST /v1/checkouts}.
 *
 * <p>The account travels in {@code metadata}, under {@link CreemCheckoutMetadata#ACCOUNT_ID_KEY},
 * because that is the field Creem copies onto the subscription and so echoes on every later
 * webhook. {@code request_id} gets a fresh identifier per checkout, purely so one checkout can be
 * found in Creem's dashboard and in this service's log.</p>
 *
 * @since 0.0.9
 */
@Service
public class CreemCheckoutGateway implements PaymentCheckoutGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreemCheckoutGateway.class);

    /** Creem's checkout endpoint, relative to the API host. */
    private static final String CHECKOUTS_PATH = "/v1/checkouts";

    /** What the caller is told when Creem does not produce a checkout; says nothing more. */
    private static final String CHECKOUT_FAILED_MESSAGE =
            "The payment provider could not open a checkout";

    private final CreemProperties creemProperties;
    private final WebApplicationProperties webApplicationProperties;
    private final RestClient creemRestClient;

    /**
     * Creates the gateway.
     *
     * @param creemProperties          API key, products and return path
     * @param webApplicationProperties the website's base URL, to return the customer to
     * @param creemRestClient          client pointed at the key's host, carrying the key
     */
    public CreemCheckoutGateway(
            final CreemProperties creemProperties,
            final WebApplicationProperties webApplicationProperties,
            @Qualifier(CreemRestClientConfiguration.CREEM_REST_CLIENT)
            final RestClient creemRestClient) {

        this.creemProperties = creemProperties;
        this.webApplicationProperties = webApplicationProperties;
        this.creemRestClient = creemRestClient;
    }

    /** {@inheritDoc} */
    @Override
    public CheckoutSession openCheckout(final CheckoutRequest checkoutRequest) {
        if (!creemProperties.isApiConfigured()) {
            throw new PaymentProviderNotConfiguredException(
                    "Payments are not enabled on this server");
        }
        final String productId = creemProperties.productIdFor(checkoutRequest.plan())
                .orElseThrow(() -> new PaymentProviderNotConfiguredException(
                        "The " + checkoutRequest.plan() + " plan is not on sale on this server"));

        final String requestId = UUID.randomUUID().toString();
        final JsonNode response = postCheckout(buildRequestBody(checkoutRequest, productId, requestId));
        final String checkoutUrl = textOrNull(response, "checkout_url");
        if (checkoutUrl == null) {
            throw new PaymentProviderRequestFailedException(CHECKOUT_FAILED_MESSAGE, null);
        }

        LOGGER.info("Opened Creem checkout {} (request {}) for account {}, plan {}",
                textOrNull(response, "id"), requestId, checkoutRequest.accountId(),
                checkoutRequest.plan());
        return new CheckoutSession(textOrNull(response, "id"), checkoutUrl);
    }

    /**
     * Builds the body of {@code POST /v1/checkouts}.
     *
     * @param checkoutRequest what is bought, by whom
     * @param productId       the Creem product selling the plan
     * @param requestId       a fresh identifier for this checkout
     * @return the body, in Creem's snake_case field names
     */
    private Map<String, Object> buildRequestBody(
            final CheckoutRequest checkoutRequest,
            final String productId,
            final String requestId) {

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("product_id", productId);
        body.put("request_id", requestId);
        body.put("metadata", Map.of(
                CreemCheckoutMetadata.ACCOUNT_ID_KEY, checkoutRequest.accountId().toString()));
        if (checkoutRequest.customerEmail() != null) {
            body.put("customer", Map.of("email", checkoutRequest.customerEmail()));
        }
        successUrl().ifPresent(url -> body.put("success_url", url));
        return body;
    }

    /**
     * Returns where Creem sends the customer after paying.
     *
     * @return the absolute URL, or empty to leave it to the product's dashboard default
     */
    private Optional<String> successUrl() {
        final String successPath = creemProperties.checkout().successPath();
        if (successPath == null || successPath.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(webApplicationProperties.baseUrl() + successPath);
    }

    /**
     * Sends the request, turning every failure into one exception.
     *
     * @param body the request body
     * @return the parsed response
     * @throws PaymentProviderRequestFailedException when Creem refuses, fails or cannot be reached
     */
    private JsonNode postCheckout(final Map<String, Object> body) {
        try {
            final JsonNode response = creemRestClient.post()
                    .uri(CHECKOUTS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new PaymentProviderRequestFailedException(CHECKOUT_FAILED_MESSAGE, null);
            }
            return response;
        } catch (final RestClientResponseException refused) {
            LOGGER.error("Creem refused a checkout with {}: {}",
                    refused.getStatusCode(), refused.getResponseBodyAsString());
            throw new PaymentProviderRequestFailedException(CHECKOUT_FAILED_MESSAGE, refused);
        } catch (final RestClientException unreachable) {
            throw new PaymentProviderRequestFailedException(CHECKOUT_FAILED_MESSAGE, unreachable);
        }
    }
}
