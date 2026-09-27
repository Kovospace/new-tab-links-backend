package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.common.exceptions.MalformedWebhookPayloadException;
import com.kovospace.newtablinks.common.exceptions.PaymentProviderNotConfiguredException;
import com.kovospace.newtablinks.common.exceptions.WebhookSignatureRejectedException;
import com.kovospace.newtablinks.payment.models.InterpretedPaymentWebhook;

/**
 * The inbound half of the payment port: turns a provider's webhook delivery into a verified,
 * provider-neutral fact.
 *
 * <p>Everything that depends on who the provider is - the signature scheme, the event names, the
 * payload shape - lives behind this interface. Replacing the provider is a new implementation of
 * it and of {@link PaymentCheckoutGateway}; nothing downstream changes.</p>
 *
 * @since 0.0.9
 */
public interface PaymentWebhookInterpreter {

    /**
     * Verifies a delivery's signature and translates it.
     *
     * <p>The signature is checked before a single byte of the body is parsed.</p>
     *
     * @param requestBody        the body exactly as received, never re-encoded
     * @param presentedSignature the signature header the delivery carried, possibly {@code null}
     * @return the verified event
     * @throws PaymentProviderNotConfiguredException when no webhook secret is configured
     * @throws WebhookSignatureRejectedException     when the signature does not match
     * @throws MalformedWebhookPayloadException      when a correctly signed body cannot be read
     */
    InterpretedPaymentWebhook verifyAndInterpret(byte[] requestBody, String presentedSignature);
}
