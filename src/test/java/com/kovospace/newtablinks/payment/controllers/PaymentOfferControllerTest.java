package com.kovospace.newtablinks.payment.controllers;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.auth.services.ProviderSignInSuccessHandler;
import com.kovospace.newtablinks.auth.services.VisitorTokenService;
import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.config.SecurityConfiguration;
import com.kovospace.newtablinks.payment.dtos.PaymentOffersDto;
import com.kovospace.newtablinks.payment.dtos.PlanOfferDto;
import com.kovospace.newtablinks.payment.models.ProPlan;
import com.kovospace.newtablinks.payment.services.PaymentOfferService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Checks, through the real security configuration, that the price list is public - even to a
 * caller carrying a stale token - and that it answers with every field of its contract.
 *
 * @since 0.0.14
 */
@WebMvcTest(PaymentOfferController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties({AuthenticationProperties.class, WebApplicationProperties.class})
class PaymentOfferControllerTest {

    private static final PaymentOffersDto OFFERS = new PaymentOffersDto("EUR", List.of(
            new PlanOfferDto(ProPlan.SUBSCRIPTION, "EUR", 468, "P1Y"),
            new PlanOfferDto(ProPlan.LIFETIME, "EUR", 1499, null)));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentOfferService paymentOfferService;

    @MockitoBean
    private VisitorTokenService visitorTokenService;

    @MockitoBean
    private ProviderSignInSuccessHandler providerSignInSuccessHandler;

    @Test
    @DisplayName("answers an anonymous caller, passing on Cloudflare's country, with every field")
    void shouldAnswerAnAnonymousCallerWithEveryField() throws Exception {
        when(paymentOfferService.describeOffersFor("SK")).thenReturn(OFFERS);

        mockMvc.perform(get(ApiEndpointPaths.PAYMENT_OFFERS_PATH).header("CF-IPCountry", "SK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedCurrency").value("EUR"))
                .andExpect(jsonPath("$.offers[0].plan").value("SUBSCRIPTION"))
                .andExpect(jsonPath("$.offers[0].currency").value("EUR"))
                .andExpect(jsonPath("$.offers[0].amountMinorUnits").value(468))
                .andExpect(jsonPath("$.offers[0].billingPeriod").value("P1Y"))
                .andExpect(jsonPath("$.offers[1].plan").value("LIFETIME"))
                .andExpect(jsonPath("$.offers[1].billingPeriod").value((Object) null));
    }

    @Test
    @DisplayName("ignores a stale bearer token rather than answering 401")
    void shouldIgnoreAStaleBearerToken() throws Exception {
        when(paymentOfferService.describeOffersFor(isNull())).thenReturn(OFFERS);

        mockMvc.perform(get(ApiEndpointPaths.PAYMENT_OFFERS_PATH)
                        .header("Authorization", "Bearer expired.or.forged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedCurrency").value("EUR"));
    }

    @Test
    @DisplayName("opens only GET: the rest of the payments prefix stays authenticated")
    void shouldOpenOnlyGetOnTheExactPath() throws Exception {
        mockMvc.perform(post(ApiEndpointPaths.PAYMENT_OFFERS_PATH))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/payments/checkouts"))
                .andExpect(status().isUnauthorized());
    }
}
