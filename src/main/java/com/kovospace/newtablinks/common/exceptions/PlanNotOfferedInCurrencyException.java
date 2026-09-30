package com.kovospace.newtablinks.common.exceptions;

/**
 * Thrown when a checkout asks for a plan in a currency that has no product configured for it.
 *
 * <p>The caller's mistake, not the server's: payments work, just not in that currency. So it is
 * answered like a rejected request field - 400, naming {@link #FIELD_NAME} - and not with the 503
 * that {@link PaymentProviderNotConfiguredException} reserves for payments being off altogether.</p>
 *
 * @since 0.0.14
 */
public class PlanNotOfferedInCurrencyException extends RuntimeException {

    /** The request field this failure is reported against. */
    public static final String FIELD_NAME = "currency";

    /**
     * Creates the exception.
     *
     * @param planName     the plan asked for
     * @param currencyCode the currency asked for
     */
    public PlanNotOfferedInCurrencyException(final String planName, final String currencyCode) {
        super("%s is not offered for the %s plan".formatted(currencyCode, planName));
    }
}
