package com.kovospace.newtablinks.payment.models;

/**
 * A checkout the payment provider has opened.
 *
 * @param providerCheckoutId the provider's identifier of the checkout
 * @param checkoutUrl        where to send the customer to pay
 * @since 0.0.9
 */
public record CheckoutSession(String providerCheckoutId, String checkoutUrl) {
}
