package com.kovospace.newtablinks.payment.mappers;

import com.kovospace.newtablinks.payment.dtos.PaymentOffersDto;
import com.kovospace.newtablinks.payment.dtos.PlanOfferDto;
import com.kovospace.newtablinks.payment.models.PaymentOffers;
import com.kovospace.newtablinks.payment.models.PlanOffer;
import org.springframework.stereotype.Component;

/**
 * Turns the offers into their response shape.
 *
 * @since 0.0.14
 */
@Component
public class PaymentOffersMapper {

    /**
     * Maps the offers and the suggestion.
     *
     * @param paymentOffers the offers
     * @return the response body
     */
    public PaymentOffersDto toDto(final PaymentOffers paymentOffers) {
        return new PaymentOffersDto(
                paymentOffers.suggestedCurrency(),
                paymentOffers.offers().stream().map(PaymentOffersMapper::toDto).toList());
    }

    /**
     * Maps one offer, writing its billing period in ISO 8601.
     *
     * @param planOffer the offer
     * @return its response shape
     */
    private static PlanOfferDto toDto(final PlanOffer planOffer) {
        return new PlanOfferDto(
                planOffer.plan(),
                planOffer.currency(),
                planOffer.amountMinorUnits(),
                planOffer.billingPeriod() == null ? null : planOffer.billingPeriod().toString());
    }
}
