package com.kovospace.newtablinks.payment.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.auth.services.ProviderSignInSuccessHandler;
import com.kovospace.newtablinks.auth.services.VisitorTokenService;
import com.kovospace.newtablinks.common.config.SecurityConfiguration;
import com.kovospace.newtablinks.common.exceptions.PlanNotOfferedInCurrencyException;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.payment.dtos.CheckoutSessionDto;
import com.kovospace.newtablinks.payment.models.ProPlan;
import com.kovospace.newtablinks.payment.services.PaymentCheckoutService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Checks the checkout's currency field: optional, passed on as sent, and a currency that does not
 * sell the plan reported as a validation failure naming the field.
 *
 * @since 0.0.14
 */
@WebMvcTest(PaymentCheckoutController.class)
@Import({SecurityConfiguration.class, AuthenticatedUserProvider.class})
@EnableConfigurationProperties({AuthenticationProperties.class, WebApplicationProperties.class})
class PaymentCheckoutControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final String CHECKOUTS_PATH = "/api/v1/payments/checkouts";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentCheckoutService paymentCheckoutService;

    @MockitoBean
    private VisitorTokenService visitorTokenService;

    @MockitoBean
    private ProviderSignInSuccessHandler providerSignInSuccessHandler;

    @Test
    @DisplayName("passes no currency on as null, for the service to default")
    void shouldPassAnOmittedCurrencyAsNull() throws Exception {
        when(paymentCheckoutService.startCheckout(eq(ACCOUNT_ID), eq(ProPlan.LIFETIME), isNull()))
                .thenReturn(new CheckoutSessionDto("https://checkout.creem.io/ch_1"));

        mockMvc.perform(checkout("{\"plan\":\"LIFETIME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.creem.io/ch_1"));
    }

    @Test
    @DisplayName("answers a currency that does not sell the plan with 400 naming `currency`")
    void shouldReportAnUnofferedCurrencyAgainstTheField() throws Exception {
        when(paymentCheckoutService.startCheckout(ACCOUNT_ID, ProPlan.LIFETIME, "gbp"))
                .thenThrow(new PlanNotOfferedInCurrencyException("LIFETIME", "GBP"));

        mockMvc.perform(checkout("{\"plan\":\"LIFETIME\",\"currency\":\"gbp\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors[0]")
                        .value("currency: GBP is not offered for the LIFETIME plan"));
    }

    @Test
    @DisplayName("refuses a malformed currency before anything is called")
    void shouldRefuseAMalformedCurrency() throws Exception {
        mockMvc.perform(checkout("{\"plan\":\"LIFETIME\",\"currency\":\"EURO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[0]")
                        .value("currency: must be a three-letter ISO 4217 currency code"));

        verify(paymentCheckoutService, never()).startCheckout(any(), any(), any());
    }

    /**
     * Builds a signed-in checkout request.
     *
     * @param body the JSON body
     * @return the request
     */
    private static RequestBuilder checkout(final String body) {
        return post(CHECKOUTS_PATH)
                .with(jwt().jwt(token -> token.subject(ACCOUNT_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
