package com.kovospace.newtablinks.payment.models;

import java.util.Objects;
import java.util.UUID;

/**
 * What a payment provider needs to open a checkout for one account.
 *
 * @param plan          what is being bought
 * @param currency      upper-case ISO 4217 code to charge in; picks which product sells the plan
 * @param accountId     the account paying; carried through the checkout so the webhook can
 *                      attribute the payment
 * @param customerEmail address to prefill on the checkout page, or {@code null} to leave it empty
 * @since 0.0.9
 */
public record CheckoutRequest(
        ProPlan plan, String currency, UUID accountId, String customerEmail) {

    /**
     * Rejects a request missing what every checkout needs.
     *
     * @throws NullPointerException when the plan, currency or account is missing
     */
    public CheckoutRequest {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(accountId, "accountId");
    }
}
