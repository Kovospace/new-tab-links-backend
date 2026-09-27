package com.kovospace.newtablinks.payment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.common.exceptions.WebhookEventInFlightException;
import com.kovospace.newtablinks.entitlement.models.PaymentProvider;
import com.kovospace.newtablinks.payment.services.PaymentWebhookClaimStore.ClaimState;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Tests which delivery of an event gets to process it.
 *
 * @since 0.0.9
 */
class PaymentWebhookClaimServiceTest {

    private static final PaymentProvider CREEM = PaymentProvider.CREEM;
    private static final String EVENT_ID = "evt_1";
    private static final String EVENT_TYPE = "checkout.completed";

    private final PaymentWebhookClaimStore claimStore = mock(PaymentWebhookClaimStore.class);
    private final PaymentWebhookClaimService claimService = new PaymentWebhookClaimService(claimStore);

    @Test
    @DisplayName("the first delivery claims the event")
    void shouldClaimANewEvent() {
        assertThat(claimService.claimForProcessing(CREEM, EVENT_ID, EVENT_TYPE)).isTrue();
    }

    @Test
    @DisplayName("a replay of a processed event is a duplicate, not an error")
    void shouldTreatAReplayOfAFinishedEventAsADuplicate() {
        claimAlreadyTaken();
        when(claimStore.findClaim(CREEM, EVENT_ID))
                .thenReturn(Optional.of(new ClaimState(true, Instant.now().minusSeconds(3600))));

        assertThat(claimService.claimForProcessing(CREEM, EVENT_ID, EVENT_TYPE)).isFalse();
        verify(claimStore, never()).deleteAbandonedClaim(any(), any(), any());
    }

    @Test
    @DisplayName("a redelivery while the first is still processing is told to come back")
    void shouldDeferARedeliveryOfAnEventInFlight() {
        claimAlreadyTaken();
        when(claimStore.findClaim(CREEM, EVENT_ID))
                .thenReturn(Optional.of(new ClaimState(false, Instant.now())));

        assertThatThrownBy(() -> claimService.claimForProcessing(CREEM, EVENT_ID, EVENT_TYPE))
                .isInstanceOf(WebhookEventInFlightException.class);
    }

    @Test
    @DisplayName("a claim abandoned by a dead process is taken over by the next delivery")
    void shouldRescueAnAbandonedClaim() {
        doThrow(new DataIntegrityViolationException("duplicate"))
                .doNothing()
                .when(claimStore).insertClaim(CREEM, EVENT_ID, EVENT_TYPE);
        when(claimStore.findClaim(CREEM, EVENT_ID)).thenReturn(Optional.of(new ClaimState(
                false, Instant.now().minus(PaymentWebhookClaimService.ABANDONED_CLAIM_AGE)
                        .minusSeconds(1))));
        when(claimStore.deleteAbandonedClaim(eq(CREEM), eq(EVENT_ID), any())).thenReturn(true);

        assertThat(claimService.claimForProcessing(CREEM, EVENT_ID, EVENT_TYPE)).isTrue();
    }

    @Test
    @DisplayName("losing the race to rescue a claim defers instead of processing twice")
    void shouldDeferWhenAnotherDeliveryRescuedFirst() {
        claimAlreadyTaken();
        when(claimStore.findClaim(CREEM, EVENT_ID)).thenReturn(Optional.of(new ClaimState(
                false, Instant.now().minus(PaymentWebhookClaimService.ABANDONED_CLAIM_AGE)
                        .minusSeconds(1))));

        assertThatThrownBy(() -> claimService.claimForProcessing(CREEM, EVENT_ID, EVENT_TYPE))
                .isInstanceOf(WebhookEventInFlightException.class);
    }

    /**
     * Makes every insert of the claim fail as a duplicate.
     */
    private void claimAlreadyTaken() {
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(claimStore).insertClaim(CREEM, EVENT_ID, EVENT_TYPE);
    }
}
