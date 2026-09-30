package com.kovospace.newtablinks.payment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kovospace.newtablinks.common.exceptions.PaymentProviderRequestFailedException;
import com.kovospace.newtablinks.payment.config.CreemProperties;
import com.kovospace.newtablinks.payment.models.ProductPrice;
import java.time.Duration;
import java.time.Period;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests how a Creem product is read as a price.
 *
 * @since 0.0.14
 */
class CreemProductCatalogTest {

    private static final String TEST_KEY = "creem_test_2B9wYk1bXQ3xR8";
    private static final String PRODUCT_URL = "https://test-api.creem.io/v1/products/prod_1";

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://test-api.creem.io").defaultHeader("x-api-key", TEST_KEY);
    private final MockRestServiceServer creem = MockRestServiceServer.bindTo(builder).build();
    private final CreemProductCatalog catalog = new CreemProductCatalog(new CreemProperties(
            TEST_KEY, null, null, Duration.ofSeconds(5), null, null), builder.build());

    @Test
    @DisplayName("reads a yearly product's price, currency and period")
    void shouldReadARecurringProduct() {
        respondWith("""
                {"id":"prod_1","price":468,"currency":"EUR","billing_type":"recurring",
                 "billing_period":"every-year","status":"active"}""");

        final ProductPrice price = catalog.readPrice("prod_1");

        creem.verify();
        assertThat(price).isEqualTo(new ProductPrice("prod_1", "EUR", 468, Period.ofYears(1)));
    }

    @Test
    @DisplayName("reads a one-time product with no period, upper-casing the currency")
    void shouldReadAOneTimeProduct() {
        respondWith("""
                {"id":"prod_1","price":1599,"currency":"usd","billing_type":"onetime",
                 "billing_period":"once"}""");

        assertThat(catalog.readPrice("prod_1"))
                .isEqualTo(new ProductPrice("prod_1", "USD", 1599, null));
    }

    @Test
    @DisplayName("refuses a product without a price, or recurring with an unknown period")
    void shouldRefuseAnUnreadableProduct() {
        respondWith("""
                {"id":"prod_1","currency":"EUR","billing_type":"onetime"}""");
        assertThatThrownBy(() -> catalog.readPrice("prod_1"))
                .isInstanceOf(PaymentProviderRequestFailedException.class);

        creem.reset();
        respondWith("""
                {"id":"prod_1","price":100,"currency":"EUR","billing_type":"recurring",
                 "billing_period":"every-fortnight"}""");
        assertThatThrownBy(() -> catalog.readPrice("prod_1"))
                .isInstanceOf(PaymentProviderRequestFailedException.class);
    }

    @Test
    @DisplayName("turns a failure at Creem into a provider failure")
    void shouldReportAFailureAtCreem() {
        creem.expect(requestTo(PRODUCT_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> catalog.readPrice("prod_1"))
                .isInstanceOf(PaymentProviderRequestFailedException.class);
    }

    /**
     * Expects one product read and answers it.
     *
     * @param productJson Creem's description of the product
     */
    private void respondWith(final String productJson) {
        creem.expect(requestTo(PRODUCT_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", TEST_KEY))
                .andRespond(withSuccess(productJson, MediaType.APPLICATION_JSON));
    }
}
