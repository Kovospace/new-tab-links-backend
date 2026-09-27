package com.kovospace.newtablinks.entitlement.models;

/**
 * The payment provider's own identifiers for a purchase, each {@code null} when not reported.
 *
 * @param customerId     the provider's customer
 * @param subscriptionId the subscription, for recurring purchases
 * @param productId      the product that was bought
 * @param orderId        the order, for one-time purchases and first subscription payments
 * @since 0.0.9
 */
public record ProviderPurchaseReferences(
        String customerId,
        String subscriptionId,
        String productId,
        String orderId) {
}
