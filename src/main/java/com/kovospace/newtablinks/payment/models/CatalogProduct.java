package com.kovospace.newtablinks.payment.models;

import java.util.Objects;

/**
 * One entry of the configured product catalog: the provider product selling a plan in a currency.
 *
 * <p>The currency is the one the catalog is keyed by, which is what the deployment says the
 * product costs in. The provider's own record of the product is authoritative over it - see
 * {@link ProductPrice#currency()}.</p>
 *
 * @param currency  upper-case ISO 4217 code the catalog files the product under
 * @param plan      the plan the product sells
 * @param productId the provider's identifier of the product
 * @since 0.0.14
 */
public record CatalogProduct(String currency, ProPlan plan, String productId) {

    /**
     * Rejects an entry missing any part.
     *
     * @throws NullPointerException when any component is missing
     */
    public CatalogProduct {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(productId, "productId");
    }
}
