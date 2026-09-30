package com.kovospace.newtablinks.payment.services;

import static com.kovospace.newtablinks.payment.utils.WebhookPayloadFields.longOrNull;
import static com.kovospace.newtablinks.payment.utils.WebhookPayloadFields.textOrNull;

import com.kovospace.newtablinks.common.exceptions.PaymentProviderRequestFailedException;
import com.kovospace.newtablinks.payment.config.CreemProperties;
import com.kovospace.newtablinks.payment.config.CreemRestClientConfiguration;
import com.kovospace.newtablinks.payment.models.CatalogProduct;
import com.kovospace.newtablinks.payment.models.ProductPrice;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Creem's implementation of {@link PaymentProductCatalog}: the catalog is
 * {@link CreemProperties#catalogProducts()}, the prices come from {@code GET /v1/products/{id}}.
 *
 * <p>Creem describes a product with {@code price} (minor units), {@code currency},
 * {@code billing_type} ({@code onetime} or {@code recurring}) and, for a recurring one,
 * {@code billing_period} ({@code every-month} ... {@code every-year}). A recurring product with a
 * period this class does not know is refused rather than guessed at, because presenting a
 * subscription as a one-time price is worse than not presenting it.</p>
 *
 * @since 0.0.14
 */
@Service
public class CreemProductCatalog implements PaymentProductCatalog {

    /** Creem's product endpoint, relative to the API host. */
    private static final String PRODUCT_PATH = "/v1/products/{productId}";

    /** Creem's {@code billing_type} of a subscription product. */
    private static final String RECURRING_BILLING_TYPE = "recurring";

    /** Creem's recurring {@code billing_period} values and what each means. */
    private static final Map<String, Period> BILLING_PERIODS = Map.of(
            "every-month", Period.ofMonths(1),
            "every-three-months", Period.ofMonths(3),
            "every-six-months", Period.ofMonths(6),
            "every-year", Period.ofYears(1));

    /** What the exception says when a price cannot be read; names no product or secret. */
    private static final String PRICE_UNREADABLE_MESSAGE =
            "The payment provider did not describe a product's price";

    private final CreemProperties creemProperties;
    private final RestClient creemRestClient;

    /**
     * Creates the catalog.
     *
     * @param creemProperties API key and the product catalog
     * @param creemRestClient client pointed at the key's host, carrying the key
     */
    public CreemProductCatalog(
            final CreemProperties creemProperties,
            @Qualifier(CreemRestClientConfiguration.CREEM_REST_CLIENT)
            final RestClient creemRestClient) {

        this.creemProperties = creemProperties;
        this.creemRestClient = creemRestClient;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAvailable() {
        return creemProperties.isApiConfigured();
    }

    /** {@inheritDoc} */
    @Override
    public List<CatalogProduct> listCatalogProducts() {
        return creemProperties.catalogProducts();
    }

    /** {@inheritDoc} */
    @Override
    public ProductPrice readPrice(final String productId) {
        final JsonNode product = fetchProduct(productId);
        final Long amountMinorUnits = longOrNull(product, "price");
        final String currency = textOrNull(product, "currency");
        if (amountMinorUnits == null || amountMinorUnits < 0 || currency == null) {
            throw new PaymentProviderRequestFailedException(PRICE_UNREADABLE_MESSAGE, null);
        }
        return new ProductPrice(productId, currency.strip().toUpperCase(Locale.ROOT),
                amountMinorUnits, billingPeriodOf(product));
    }

    /**
     * Reads how often a product is charged.
     *
     * @param product Creem's description of the product
     * @return the period, or {@code null} for a one-time product
     * @throws PaymentProviderRequestFailedException when it is recurring with an unknown period
     */
    private static Period billingPeriodOf(final JsonNode product) {
        if (!RECURRING_BILLING_TYPE.equals(textOrNull(product, "billing_type"))) {
            return null;
        }
        final String creemBillingPeriod = textOrNull(product, "billing_period");
        final Period billingPeriod = creemBillingPeriod == null
                ? null
                : BILLING_PERIODS.get(creemBillingPeriod);
        if (billingPeriod == null) {
            throw new PaymentProviderRequestFailedException(PRICE_UNREADABLE_MESSAGE, null);
        }
        return billingPeriod;
    }

    /**
     * Retrieves a product, turning every failure into one exception.
     *
     * @param productId the product
     * @return Creem's description of it
     * @throws PaymentProviderRequestFailedException when Creem refuses, fails or cannot be reached
     */
    private JsonNode fetchProduct(final String productId) {
        try {
            final JsonNode product = creemRestClient.get()
                    .uri(PRODUCT_PATH, productId)
                    .retrieve()
                    .body(JsonNode.class);
            if (product == null) {
                throw new PaymentProviderRequestFailedException(PRICE_UNREADABLE_MESSAGE, null);
            }
            return product;
        } catch (final RestClientException failure) {
            throw new PaymentProviderRequestFailedException(PRICE_UNREADABLE_MESSAGE, failure);
        }
    }
}
