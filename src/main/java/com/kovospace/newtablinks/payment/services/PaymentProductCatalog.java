package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.common.exceptions.PaymentProviderRequestFailedException;
import com.kovospace.newtablinks.payment.models.CatalogProduct;
import com.kovospace.newtablinks.payment.models.ProductPrice;
import java.util.List;

/**
 * The outbound port for what is on sale: the configured products, and what the provider says
 * each costs.
 *
 * <p>Reading a price is a call to the provider on every invocation. Nothing serving a public
 * request may call {@link #readPrice(String)} directly - {@link ProductPriceCache} stands in
 * front of it.</p>
 *
 * @since 0.0.14
 */
public interface PaymentProductCatalog {

    /**
     * Tells whether prices can be read at all.
     *
     * @return {@code false} when the provider is not configured on this server
     */
    boolean isAvailable();

    /**
     * Lists every product configured for sale.
     *
     * @return one entry per currency and plan with a product; empty when nothing is on sale
     */
    List<CatalogProduct> listCatalogProducts();

    /**
     * Asks the provider what a product costs.
     *
     * @param productId the provider's product identifier
     * @return the price, in the currency and billing period the provider reports
     * @throws PaymentProviderRequestFailedException when the provider refuses, fails, cannot be
     *                                               reached or describes the product in a way
     *                                               that cannot be read as a price
     */
    ProductPrice readPrice(String productId);
}
