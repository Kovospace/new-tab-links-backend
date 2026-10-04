package com.kovospace.newtablinks.payment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.common.exceptions.PaymentProviderNotConfiguredException;
import com.kovospace.newtablinks.common.exceptions.PaymentProviderRequestFailedException;
import com.kovospace.newtablinks.common.exceptions.PlanNotOfferedInCurrencyException;
import com.kovospace.newtablinks.payment.config.CreemProperties;
import com.kovospace.newtablinks.payment.models.CheckoutRequest;
import com.kovospace.newtablinks.payment.models.CheckoutSession;
import com.kovospace.newtablinks.payment.models.CreemCheckoutMetadata;
import com.kovospace.newtablinks.payment.models.ProPlan;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests the checkout request this service sends Creem - above all that the paying account travels
 * in the metadata Creem echoes back on every webhook.
 *
 * @since 0.0.9
 */
class CreemCheckoutGatewayTest {

    private static final String TEST_KEY = "creem_test_2B9wYk1bXQ3xR8";
    private static final UUID ACCOUNT_ID = UUID.fromString("0c9a3a8e-5a8b-4a7e-9b3c-3f1f2c4d5e6f");
    private static final CreemProperties CONFIGURED = new CreemProperties(
            TEST_KEY, "whsec_x", null, Duration.ofSeconds(5),
            Map.of("EUR", new CreemProperties.Products("prod_lifetime", "prod_yearly"),
                    "usd", new CreemProperties.Products("prod_lifetime_usd", "prod_yearly_usd")),
            new CreemProperties.Checkout("/account"));

    @Test
    @DisplayName("sends the product, the key and the account in metadata to the sandbox host")
    void shouldCarryTheAccountThroughCheckoutMetadata() {
        final RestClient.Builder builder = RestClient.builder();
        final MockRestServiceServer creem = MockRestServiceServer.bindTo(builder).build();
        creem.expect(requestTo("https://test-api.creem.io/v1/checkouts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", TEST_KEY))
                .andExpect(jsonPath("$.product_id").value("prod_lifetime"))
                .andExpect(jsonPath("$.metadata." + CreemCheckoutMetadata.ACCOUNT_ID_KEY)
                        .value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.customer.email").value("buyer@example.com"))
                .andExpect(jsonPath("$.success_url").value("https://newtablinks.example/account"))
                .andExpect(jsonPath("$.request_id").isNotEmpty())
                .andRespond(withSuccess("""
                        {"id":"ch_1","checkout_url":"https://checkout.creem.io/ch_1",
                         "status":"pending"}""", MediaType.APPLICATION_JSON));

        final CheckoutSession session = gatewayFor(CONFIGURED, builder).openCheckout(
                new CheckoutRequest(ProPlan.LIFETIME, "EUR", ACCOUNT_ID, "buyer@example.com"));

        creem.verify();
        assertThat(session.checkoutUrl()).isEqualTo("https://checkout.creem.io/ch_1");
        assertThat(session.providerCheckoutId()).isEqualTo("ch_1");
    }

    @Test
    @DisplayName("turns a refusal from Creem into a provider failure")
    void shouldReportARefusal() {
        final RestClient.Builder builder = RestClient.builder();
        final MockRestServiceServer creem = MockRestServiceServer.bindTo(builder).build();
        creem.expect(requestTo("https://test-api.creem.io/v1/checkouts"))
                .andRespond(withBadRequest().body("{\"trace_id\":\"t1\"}"));

        assertThatThrownBy(() -> gatewayFor(CONFIGURED, builder).openCheckout(
                new CheckoutRequest(ProPlan.SUBSCRIPTION, "EUR", ACCOUNT_ID, null)))
                .isInstanceOf(PaymentProviderRequestFailedException.class);
    }

    @Test
    @DisplayName("opens the checkout for the USD product when the customer pays in USD")
    void shouldChooseTheProductOfTheRequestedCurrency() {
        final RestClient.Builder builder = RestClient.builder();
        final MockRestServiceServer creem = MockRestServiceServer.bindTo(builder).build();
        creem.expect(requestTo("https://test-api.creem.io/v1/checkouts"))
                .andExpect(jsonPath("$.product_id").value("prod_yearly_usd"))
                .andRespond(withSuccess("""
                        {"id":"ch_2","checkout_url":"https://checkout.creem.io/ch_2"}""",
                        MediaType.APPLICATION_JSON));

        final CheckoutSession session = gatewayFor(CONFIGURED, builder).openCheckout(
                new CheckoutRequest(ProPlan.SUBSCRIPTION, "USD", ACCOUNT_ID, null));

        creem.verify();
        assertThat(session.checkoutUrl()).isEqualTo("https://checkout.creem.io/ch_2");
    }

    @Test
    @DisplayName("refuses with 503's exception without a key, and names the currency when it "
            + "does not sell the plan")
    void shouldRefuseWhenNotConfigured() {
        final CreemProperties noKey = new CreemProperties(
                null, null, null, Duration.ofSeconds(5), null, null);
        final CreemProperties noEuroSubscription = new CreemProperties(
                TEST_KEY, null, null, Duration.ofSeconds(5),
                Map.of("EUR", new CreemProperties.Products("prod_lifetime", "")), null);
        final CheckoutRequest euroSubscription =
                new CheckoutRequest(ProPlan.SUBSCRIPTION, "EUR", ACCOUNT_ID, null);

        assertThatThrownBy(() -> gatewayFor(noKey, RestClient.builder())
                .openCheckout(euroSubscription))
                .isInstanceOf(PaymentProviderNotConfiguredException.class);
        assertThatThrownBy(() -> gatewayFor(noEuroSubscription, RestClient.builder())
                .openCheckout(euroSubscription))
                .isInstanceOf(PlanNotOfferedInCurrencyException.class)
                .hasMessage("EUR is not offered for the SUBSCRIPTION plan");
        assertThatThrownBy(() -> gatewayFor(CONFIGURED, RestClient.builder())
                .openCheckout(new CheckoutRequest(ProPlan.LIFETIME, "GBP", ACCOUNT_ID, null)))
                .isInstanceOf(PlanNotOfferedInCurrencyException.class);
    }

    /**
     * Builds the gateway on a client pointed at the key's host, as the real configuration does.
     *
     * @param properties the Creem configuration
     * @param builder    a builder, possibly bound to a mock server
     * @return the gateway
     */
    private static CreemCheckoutGateway gatewayFor(
            final CreemProperties properties, final RestClient.Builder builder) {

        properties.apiMode().ifPresent(mode -> builder.baseUrl(mode.apiBaseUrl())
                .defaultHeader("x-api-key", properties.apiKey()));
        return new CreemCheckoutGateway(properties, websiteAt("https://newtablinks.example"),
                builder.build());
    }

    /**
     * Website configuration with only the base URL that matters here.
     *
     * @param baseUrl the website's base URL
     * @return the configuration
     */
    private static WebApplicationProperties websiteAt(final String baseUrl) {
        return new WebApplicationProperties(baseUrl, "/activate", "/auth/callback",
                "/reset-password", "", "/devices");
    }
}
