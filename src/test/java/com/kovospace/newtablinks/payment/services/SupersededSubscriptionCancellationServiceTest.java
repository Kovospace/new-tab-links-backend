package com.kovospace.newtablinks.payment.services;

import static com.kovospace.newtablinks.payment.services.CreemSubscriptionCancellerTest.CANCEL_URL;
import static com.kovospace.newtablinks.payment.services.CreemSubscriptionCancellerTest.CONFIGURED;
import static com.kovospace.newtablinks.payment.services.CreemSubscriptionCancellerTest.TEST_KEY;
import static com.kovospace.newtablinks.payment.services.CreemSubscriptionCancellerTest.cancellerFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kovospace.newtablinks.entitlement.models.SupersededSubscriptionCancellation;
import com.kovospace.newtablinks.entitlement.services.SupersededSubscriptionService;
import com.kovospace.newtablinks.payment.config.CreemProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

/**
 * Tests that a superseded subscription is cancelled at Creem, that a failure keeps it pending, and
 * that the retry job finishes it.
 *
 * @since 0.0.9
 */
class SupersededSubscriptionCancellationServiceTest {

    private static final SupersededSubscriptionCancellation PENDING =
            new SupersededSubscriptionCancellation(UUID.randomUUID(), "sub_1");

    private final SupersededSubscriptionService supersededSubscriptionService =
            mock(SupersededSubscriptionService.class);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer creem = MockRestServiceServer.bindTo(builder).build();

    @Test
    @DisplayName("the attempt after commit calls Creem's cancel endpoint and marks the row done")
    void shouldCancelAtCreemAndMarkTheRowDone() {
        creem.expect(requestTo(CANCEL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", TEST_KEY))
                .andRespond(canceledSubscription());

        final boolean done = serviceFor(CONFIGURED).attemptCancellation(PENDING);

        creem.verify();
        assertThat(done).isTrue();
        verify(supersededSubscriptionService).markCancelled(eq(PENDING), any(Instant.class));
    }

    @Test
    @DisplayName("a failed attempt keeps the cancellation pending, and the retry job clears it")
    void shouldKeepAFailedCancellationPendingUntilTheRetrySucceeds() {
        creem.expect(requestTo(CANCEL_URL)).andRespond(withServiceUnavailable());
        creem.expect(requestTo(CANCEL_URL)).andRespond(canceledSubscription());
        final SupersededSubscriptionCancellationService service = serviceFor(CONFIGURED);

        final boolean firstAttemptDone = service.attemptCancellation(PENDING);
        verify(supersededSubscriptionService, never()).markCancelled(any(), any());

        when(supersededSubscriptionService.findPendingCancellations(anyInt()))
                .thenReturn(List.of(PENDING));
        final int completedByRetry = service.retryPendingCancellations();

        creem.verify();
        assertThat(firstAttemptDone).isFalse();
        assertThat(completedByRetry).isEqualTo(1);
        verify(supersededSubscriptionService).markCancelled(eq(PENDING), any(Instant.class));
    }

    @Test
    @DisplayName("with payments disabled nothing is attempted and the rows stay pending")
    void shouldDoNothingWhilePaymentsAreDisabled() {
        final CreemProperties noKey =
                new CreemProperties(null, null, null, Duration.ofSeconds(5), null, null);
        final SupersededSubscriptionCancellationService service = serviceFor(noKey);

        assertThat(service.retryPendingCancellations()).isZero();
        assertThat(service.retryPendingCancellations()).isZero();
        assertThat(service.attemptCancellation(PENDING)).isFalse();

        verifyNoInteractions(supersededSubscriptionService);
        creem.verify();
    }

    /**
     * Builds the service on the real Creem canceller, pointed at the mock server.
     *
     * @param properties the Creem configuration
     * @return the service
     */
    private SupersededSubscriptionCancellationService serviceFor(final CreemProperties properties) {
        return new SupersededSubscriptionCancellationService(
                cancellerFor(properties, builder), supersededSubscriptionService);
    }

    /**
     * Creem's answer to a successful cancellation.
     *
     * @return the response
     */
    private static ResponseCreator canceledSubscription() {
        return withSuccess("{\"id\":\"sub_1\",\"status\":\"canceled\"}",
                MediaType.APPLICATION_JSON);
    }
}
