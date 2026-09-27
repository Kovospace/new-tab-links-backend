package com.kovospace.newtablinks.payment.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.auth.services.ProviderSignInSuccessHandler;
import com.kovospace.newtablinks.auth.services.VisitorTokenService;
import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.config.SecurityConfiguration;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.payment.mappers.SubscriptionStatusMapper;
import com.kovospace.newtablinks.payment.services.PaymentSubscriptionService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Checks, through the real security configuration, that the subscription endpoint is not opened
 * by the webhook's exemption and answers with every field of its contract.
 *
 * @since 0.0.9
 */
@WebMvcTest(PaymentSubscriptionController.class)
@Import({SecurityConfiguration.class, AuthenticatedUserProvider.class})
@EnableConfigurationProperties({AuthenticationProperties.class, WebApplicationProperties.class})
class PaymentSubscriptionControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentSubscriptionService paymentSubscriptionService;

    @MockitoBean
    private VisitorTokenService visitorTokenService;

    @MockitoBean
    private ProviderSignInSuccessHandler providerSignInSuccessHandler;

    @Test
    @DisplayName("refuses a request without an access token")
    void shouldRefuseRequestWithoutAccessToken() throws Exception {
        mockMvc.perform(get(ApiEndpointPaths.PAYMENT_SUBSCRIPTION_PATH))
                .andExpect(status().isUnauthorized());

        verify(paymentSubscriptionService, never()).describeSubscriptionOf(any());
    }

    @Test
    @DisplayName("answers a signed-in account with every field, nulls included")
    void shouldAnswerSignedInAccountWithEveryField() throws Exception {
        when(paymentSubscriptionService.describeSubscriptionOf(ACCOUNT_ID))
                .thenReturn(new SubscriptionStatusMapper().toDtoForAccountWithoutEntitlement());

        mockMvc.perform(get(ApiEndpointPaths.PAYMENT_SUBSCRIPTION_PATH)
                        .with(jwt().jwt(token -> token.subject(ACCOUNT_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value((Object) null))
                .andExpect(jsonPath("$.state").value("NONE"))
                .andExpect(jsonPath("$.startedAt").value((Object) null))
                .andExpect(jsonPath("$.validUntil").value((Object) null))
                .andExpect(jsonPath("$.renewsAt").value((Object) null))
                .andExpect(jsonPath("$.cancelledAt").value((Object) null))
                .andExpect(jsonPath("$.cancellable").value(false))
                .andExpect(jsonPath("$.refundable").value(false))
                .andExpect(jsonPath("$.refundableUntil").value((Object) null))
                .andExpect(jsonPath("$.pendingCheckout").value((Object) null));
    }
}
