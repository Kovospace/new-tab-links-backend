package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.common.exceptions.PaymentProviderNotConfiguredException;
import com.kovospace.newtablinks.common.exceptions.PaymentProviderRequestFailedException;
import com.kovospace.newtablinks.common.exceptions.PlanNotOfferedInCurrencyException;
import com.kovospace.newtablinks.payment.config.PaymentPricingProperties;
import com.kovospace.newtablinks.payment.dtos.CheckoutSessionDto;
import com.kovospace.newtablinks.payment.models.CheckoutRequest;
import com.kovospace.newtablinks.payment.models.CheckoutSession;
import com.kovospace.newtablinks.payment.models.ProPlan;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.services.UserService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Starts a purchase for the signed-in account.
 *
 * <p>Deliberately not transactional: the only database read is the account, and holding a
 * connection open across a call to the provider would tie the pool to the provider's latency.</p>
 *
 * @since 0.0.9
 */
@Service
public class PaymentCheckoutService {

    /**
     * Domain of the placeholder address an account gets from a provider that returned none.
     * Prefilling it would only make the customer delete it.
     */
    private static final String UNDELIVERABLE_EMAIL_SUFFIX = "@no-address.invalid";

    private final PaymentCheckoutGateway paymentCheckoutGateway;
    private final UserService userService;
    private final PaymentPricingProperties paymentPricingProperties;

    /**
     * Creates the service.
     *
     * @param paymentCheckoutGateway   the provider adapter that opens checkouts
     * @param userService              resolves the paying account
     * @param paymentPricingProperties supplies the currency of a checkout that names none
     */
    public PaymentCheckoutService(
            final PaymentCheckoutGateway paymentCheckoutGateway,
            final UserService userService,
            final PaymentPricingProperties paymentPricingProperties) {

        this.paymentCheckoutGateway = paymentCheckoutGateway;
        this.userService = userService;
        this.paymentPricingProperties = paymentPricingProperties;
    }

    /**
     * Opens a checkout for a plan in a currency, on behalf of an account.
     *
     * @param accountId the paying account, taken from the access token
     * @param plan      what is being bought
     * @param currency  ISO 4217 code in any case, or {@code null} for the default currency
     * @return where to send the customer to pay
     * @throws PlanNotOfferedInCurrencyException     when the currency does not sell the plan
     * @throws PaymentProviderNotConfiguredException when payments are off on this server
     * @throws PaymentProviderRequestFailedException when the provider refuses or fails
     */
    public CheckoutSessionDto startCheckout(
            final UUID accountId, final ProPlan plan, final String currency) {

        final UserEntity account = userService.getRequiredUserEntity(accountId);
        final CheckoutSession session = paymentCheckoutGateway.openCheckout(new CheckoutRequest(
                plan, currencyOrDefault(currency), accountId, prefillableEmailOf(account)));
        return new CheckoutSessionDto(session.checkoutUrl());
    }

    /**
     * Upper-cases the requested currency, or falls back to the configured default.
     *
     * @param currency the requested code, possibly {@code null} or blank
     * @return an upper-case currency code
     */
    private String currencyOrDefault(final String currency) {
        return currency == null || currency.isBlank()
                ? paymentPricingProperties.defaultCurrency()
                : currency.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * Returns the account's address when it is one worth prefilling.
     *
     * @param account the paying account
     * @return the address, or {@code null} for a placeholder
     */
    private static String prefillableEmailOf(final UserEntity account) {
        final String email = account.getEmail();
        return email == null || email.endsWith(UNDELIVERABLE_EMAIL_SUFFIX) ? null : email;
    }
}
