package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.common.exceptions.PaymentProviderNotConfiguredException;
import com.kovospace.newtablinks.common.exceptions.PaymentProviderRequestFailedException;
import com.kovospace.newtablinks.payment.models.CheckoutRequest;
import com.kovospace.newtablinks.payment.models.CheckoutSession;

/**
 * The outbound half of the payment port: asks the provider to open a checkout.
 *
 * @since 0.0.9
 */
public interface PaymentCheckoutGateway {

    /**
     * Opens a checkout the customer can pay at.
     *
     * <p>The implementation must carry {@link CheckoutRequest#accountId()} through the checkout in
     * a way the provider echoes back on every later webhook for the resulting purchase - without
     * it, a payment cannot be attributed to anybody.</p>
     *
     * @param checkoutRequest what is bought, by whom
     * @return the opened checkout
     * @throws PaymentProviderNotConfiguredException when the provider or the plan's product is not
     *                                               configured
     * @throws PaymentProviderRequestFailedException when the provider refuses or cannot be reached
     */
    CheckoutSession openCheckout(CheckoutRequest checkoutRequest);
}
