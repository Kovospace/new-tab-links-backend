package com.kovospace.newtablinks.payment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.payment.config.PaymentPricingProperties;
import com.kovospace.newtablinks.payment.models.CheckoutRequest;
import com.kovospace.newtablinks.payment.models.CheckoutSession;
import com.kovospace.newtablinks.payment.models.ProPlan;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.services.UserService;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests the currency a checkout is opened in: as requested, upper-cased, or the default.
 *
 * @since 0.0.14
 */
class PaymentCheckoutServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

    private final PaymentCheckoutGateway gateway = mock(PaymentCheckoutGateway.class);
    private final UserService userService = mock(UserService.class);
    private final PaymentCheckoutService service = new PaymentCheckoutService(gateway, userService,
            new PaymentPricingProperties("USD", Map.of(), Duration.ofHours(1),
                    Duration.ofMinutes(5)));

    /** A paying account and a gateway that always opens a checkout. */
    @BeforeEach
    void configureAccountAndGateway() {
        final UserEntity account = mock(UserEntity.class);
        when(account.getEmail()).thenReturn("buyer@example.com");
        when(userService.getRequiredUserEntity(ACCOUNT_ID)).thenReturn(account);
        when(gateway.openCheckout(any()))
                .thenReturn(new CheckoutSession("ch_1", "https://checkout.creem.io/ch_1"));
    }

    @Test
    @DisplayName("opens the checkout in the default currency when none is named")
    void shouldUseTheDefaultCurrencyWhenNoneIsNamed() {
        service.startCheckout(ACCOUNT_ID, ProPlan.LIFETIME, null);

        assertThat(sentRequest().currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("opens the checkout in the named currency, upper-cased")
    void shouldUseTheNamedCurrency() {
        service.startCheckout(ACCOUNT_ID, ProPlan.SUBSCRIPTION, "eur");

        assertThat(sentRequest().currency()).isEqualTo("EUR");
        assertThat(sentRequest().plan()).isEqualTo(ProPlan.SUBSCRIPTION);
    }

    /**
     * Returns what the service asked the gateway for.
     *
     * @return the checkout request
     */
    private CheckoutRequest sentRequest() {
        final ArgumentCaptor<CheckoutRequest> captor = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(gateway).openCheckout(captor.capture());
        return captor.getValue();
    }
}
