package com.kovospace.newtablinks.payment.models;

/**
 * What a customer can buy to become pro.
 *
 * <p>Deliberately a closed choice rather than a product identifier from the caller: which
 * provider product sells each plan is configuration, so the website never learns a product id
 * and a caller cannot open a checkout for anything else in the store.</p>
 *
 * @since 0.0.9
 */
public enum ProPlan {

    /** One payment, pro for good. */
    LIFETIME,

    /** Billed yearly, pro while it is paid. */
    SUBSCRIPTION
}
