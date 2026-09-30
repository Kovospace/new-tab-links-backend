package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.common.exceptions.PaymentProviderRequestFailedException;
import com.kovospace.newtablinks.payment.config.PaymentPricingProperties;
import com.kovospace.newtablinks.payment.models.CatalogProduct;
import com.kovospace.newtablinks.payment.models.ProductPrice;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The last known price of every catalog product, read from the provider at most once per
 * {@link PaymentPricingProperties#priceCacheLifetime()}.
 *
 * <p><strong>Traffic here never becomes traffic at the provider.</strong> The offers endpoint is
 * public and sits behind the home page, so a crawler can hit it as often as it likes; what it
 * reaches is this in-memory map. The provider is asked only when the map is due for a refresh,
 * by one request at a time - a request arriving while another refreshes is answered from what is
 * already known rather than queued behind the provider. Each replica keeps its own map, so the
 * provider sees at most one refresh per replica per lifetime.</p>
 *
 * <p><strong>A failed read keeps the last known price.</strong> A product whose price has never
 * been read successfully is simply absent. After any failed read the next refresh is due after
 * {@link PaymentPricingProperties#priceRefreshRetryInterval()} instead of the full lifetime, so an
 * outage heals quickly without the provider being asked on every request meanwhile.</p>
 *
 * @since 0.0.14
 */
@Service
public class ProductPriceCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductPriceCache.class);

    private final PaymentProductCatalog paymentProductCatalog;
    private final PaymentPricingProperties paymentPricingProperties;
    private final Clock clock;

    /** Held by the one request refreshing the map; others do not wait for it. */
    private final ReentrantLock refreshLock = new ReentrantLock();

    /** Product identifier to its last known price; replaced whole, never mutated. */
    private volatile Map<String, ProductPrice> knownPricesByProductId = Map.of();

    /** When the map is next read from the provider; the distant past until the first read. */
    private volatile Instant nextRefreshDueAt = Instant.MIN;

    /**
     * Creates the cache, empty; the first request reads the prices.
     *
     * @param paymentProductCatalog    the catalog and the provider's prices
     * @param paymentPricingProperties how long a price is trusted, and how soon to retry
     * @param clock                    the clock refreshes are timed by
     */
    public ProductPriceCache(
            final PaymentProductCatalog paymentProductCatalog,
            final PaymentPricingProperties paymentPricingProperties,
            final Clock clock) {

        this.paymentProductCatalog = paymentProductCatalog;
        this.paymentPricingProperties = paymentPricingProperties;
        this.clock = clock;
    }

    /**
     * Returns every known price, refreshing them first when they are due and nobody else is.
     *
     * @return product identifier to price; unmodifiable, and empty when no price has ever been
     *         read
     */
    public Map<String, ProductPrice> knownPricesByProductId() {
        if (!isRefreshDue() || !refreshLock.tryLock()) {
            return knownPricesByProductId;
        }
        try {
            if (isRefreshDue()) {
                refreshFromProvider();
            }
        } finally {
            refreshLock.unlock();
        }
        return knownPricesByProductId;
    }

    /**
     * Tells whether the map is due to be read again.
     *
     * @return {@code true} once the next refresh time has passed
     */
    private boolean isRefreshDue() {
        return !clock.instant().isBefore(nextRefreshDueAt);
    }

    /**
     * Reads every catalog product's price, keeping the last known one where a read fails, and
     * schedules the next refresh.
     */
    private void refreshFromProvider() {
        final Map<String, ProductPrice> refreshedPrices = new HashMap<>(knownPricesByProductId);
        boolean hasReadEveryPrice = true;
        for (final CatalogProduct catalogProduct : paymentProductCatalog.listCatalogProducts()) {
            try {
                final ProductPrice price = paymentProductCatalog.readPrice(catalogProduct.productId());
                warnWhenCurrencyDisagrees(catalogProduct, price);
                refreshedPrices.put(catalogProduct.productId(), price);
            } catch (final PaymentProviderRequestFailedException failure) {
                hasReadEveryPrice = false;
                LOGGER.warn("Could not read the price of product {} ({} {}); serving the last "
                                + "known one, if any: {}", catalogProduct.productId(),
                        catalogProduct.currency(), catalogProduct.plan(), describe(failure));
            }
        }
        knownPricesByProductId = Map.copyOf(refreshedPrices);
        nextRefreshDueAt = clock.instant().plus(hasReadEveryPrice
                ? paymentPricingProperties.priceCacheLifetime()
                : paymentPricingProperties.priceRefreshRetryInterval());
    }

    /**
     * Logs a product filed under one currency that the provider charges in another. The
     * provider's currency is the one used - it is what the customer will actually pay.
     *
     * @param catalogProduct the catalog entry
     * @param price          what the provider reported
     */
    private static void warnWhenCurrencyDisagrees(
            final CatalogProduct catalogProduct, final ProductPrice price) {

        if (!catalogProduct.currency().equals(price.currency())) {
            LOGGER.warn("Product {} is configured as the {} {} product, but the payment provider "
                            + "charges it in {}; offering it in {}", catalogProduct.productId(),
                    catalogProduct.currency(), catalogProduct.plan(), price.currency(),
                    price.currency());
        }
    }

    /**
     * Describes a failure by its cause, which is where the provider's own reason is.
     *
     * @param failure the failure
     * @return a one-line description
     */
    private static String describe(final PaymentProviderRequestFailedException failure) {
        return failure.getCause() == null ? failure.getMessage() : failure.getCause().toString();
    }
}
