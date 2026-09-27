package com.kovospace.newtablinks.payment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kovospace.newtablinks.common.exceptions.PaymentProviderRequestFailedException;
import com.kovospace.newtablinks.payment.config.CreemProperties;
import com.kovospace.newtablinks.payment.models.SubscriptionCancellationResult;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests the request that cancels a superseded subscription at Creem, and how a refusal is judged.
 *
 * @since 0.0.9
 */
class CreemSubscriptionCancellerTest {

    static final String TEST_KEY = "creem_test_2B9wYk1bXQ3xR8";
    static final String CANCEL_URL = "https://test-api.creem.io/v1/subscriptions/sub_1/cancel";
    static final String RETRIEVE_URL =
            "https://test-api.creem.io/v1/subscriptions?subscription_id=sub_1";
    static final CreemProperties CONFIGURED = new CreemProperties(
            TEST_KEY, "whsec_x", null, Duration.ofSeconds(5), null, null);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer creem = MockRestServiceServer.bindTo(builder).build();

    @Test
    @DisplayName("posts an immediate cancellation to the subscription's cancel endpoint with the key")
    void shouldCancelImmediately() {
        creem.expect(requestTo(CANCEL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", TEST_KEY))
                .andExpect(jsonPath("$.mode").value("immediate"))
                .andRespond(withSuccess("{\"id\":\"sub_1\",\"status\":\"canceled\"}",
                        MediaType.APPLICATION_JSON));

        final SubscriptionCancellationResult result =
                cancellerFor(CONFIGURED, builder).cancelImmediately("sub_1");

        creem.verify();
        assertThat(result).isEqualTo(SubscriptionCancellationResult.CANCELLED);
    }

    @Test
    @DisplayName("a refusal for a subscription Creem already shows as canceled counts as done")
    void shouldTreatAnAlreadyCancelledSubscriptionAsDone() {
        creem.expect(requestTo(CANCEL_URL)).andRespond(withBadRequest());
        creem.expect(requestTo(RETRIEVE_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", TEST_KEY))
                .andRespond(withSuccess("{\"id\":\"sub_1\",\"status\":\"canceled\"}",
                        MediaType.APPLICATION_JSON));

        final SubscriptionCancellationResult result =
                cancellerFor(CONFIGURED, builder).cancelImmediately("sub_1");

        creem.verify();
        assertThat(result).isEqualTo(SubscriptionCancellationResult.ALREADY_NOT_RENEWING);
    }

    @Test
    @DisplayName("a refusal for a subscription scheduled to cancel counts as done - it will not "
            + "charge again")
    void shouldTreatARefusedScheduledCancellationAsDone() {
        creem.expect(requestTo(CANCEL_URL)).andRespond(withBadRequest());
        creem.expect(requestTo(RETRIEVE_URL)).andRespond(withSuccess(
                "{\"id\":\"sub_1\",\"status\":\"scheduled_cancel\"}", MediaType.APPLICATION_JSON));

        assertThat(cancellerFor(CONFIGURED, builder).cancelImmediately("sub_1"))
                .isEqualTo(SubscriptionCancellationResult.ALREADY_NOT_RENEWING);
    }

    @Test
    @DisplayName("a refusal for a subscription still active is a failure")
    void shouldFailWhenRefusedAndStillActive() {
        creem.expect(requestTo(CANCEL_URL)).andRespond(withBadRequest());
        creem.expect(requestTo(RETRIEVE_URL)).andRespond(withSuccess(
                "{\"id\":\"sub_1\",\"status\":\"active\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cancellerFor(CONFIGURED, builder).cancelImmediately("sub_1"))
                .isInstanceOf(PaymentProviderRequestFailedException.class)
                .hasMessageContaining("sub_1");
    }

    @Test
    @DisplayName("a server error is a failure, and does not read the subscription back")
    void shouldFailOnAServerError() {
        creem.expect(requestTo(CANCEL_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> cancellerFor(CONFIGURED, builder).cancelImmediately("sub_1"))
                .isInstanceOf(PaymentProviderRequestFailedException.class);
        creem.verify();
    }

    @Test
    @DisplayName("is disabled without an API key")
    void shouldBeDisabledWithoutAKey() {
        final CreemProperties noKey =
                new CreemProperties(null, null, null, Duration.ofSeconds(5), null, null);

        assertThat(cancellerFor(noKey, RestClient.builder()).isEnabled()).isFalse();
        assertThat(cancellerFor(CONFIGURED, RestClient.builder()).isEnabled()).isTrue();
    }

    /**
     * Builds the canceller on a client pointed at the key's host, as the real configuration does.
     *
     * @param properties the Creem configuration
     * @param builder    a builder, possibly bound to a mock server
     * @return the canceller
     */
    static CreemSubscriptionCanceller cancellerFor(
            final CreemProperties properties, final RestClient.Builder builder) {

        properties.apiMode().ifPresent(mode -> builder.baseUrl(mode.apiBaseUrl())
                .defaultHeader("x-api-key", properties.apiKey()));
        return new CreemSubscriptionCanceller(properties, builder.build());
    }
}
