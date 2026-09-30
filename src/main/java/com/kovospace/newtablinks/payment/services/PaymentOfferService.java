package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.payment.dtos.PaymentOffersDto;
import com.kovospace.newtablinks.payment.mappers.PaymentOffersMapper;
import com.kovospace.newtablinks.payment.models.CatalogProduct;
import com.kovospace.newtablinks.payment.models.PaymentOffers;
import com.kovospace.newtablinks.payment.models.PlanOffer;
import com.kovospace.newtablinks.payment.models.ProPlan;
import com.kovospace.newtablinks.payment.models.ProductPrice;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Lists what is on sale, at what price, and which currency to show a visitor first.
 *
 * <p>An offer exists for every catalog product whose price is known - from the provider, through
 * {@link ProductPriceCache}. A product whose price has never been read is left out rather than
 * shown at a made-up price. With payments not configured the answer is an empty list, not an
 * error: the website then simply has nothing to sell.</p>
 *
 * @since 0.0.14
 */
@Service
public class PaymentOfferService {

    /** Offers are grouped by currency, and within one the subscription comes first. */
    private static final Comparator<PlanOffer> OFFER_ORDER = Comparator
            .comparing(PlanOffer::currency)
            .thenComparing(offer -> offer.plan() != ProPlan.SUBSCRIPTION);

    private final PaymentProductCatalog paymentProductCatalog;
    private final ProductPriceCache productPriceCache;
    private final CurrencySuggestionPolicy currencySuggestionPolicy;
    private final PaymentOffersMapper paymentOffersMapper;

    /**
     * Creates the service.
     *
     * @param paymentProductCatalog    the configured products, and whether payments are on
     * @param productPriceCache        the last known price of each product
     * @param currencySuggestionPolicy picks the currency to show first
     * @param paymentOffersMapper      turns the offers into their response shape
     */
    public PaymentOfferService(
            final PaymentProductCatalog paymentProductCatalog,
            final ProductPriceCache productPriceCache,
            final CurrencySuggestionPolicy currencySuggestionPolicy,
            final PaymentOffersMapper paymentOffersMapper) {

        this.paymentProductCatalog = paymentProductCatalog;
        this.productPriceCache = productPriceCache;
        this.currencySuggestionPolicy = currencySuggestionPolicy;
        this.paymentOffersMapper = paymentOffersMapper;
    }

    /**
     * Describes every offer and the currency suggested for a country.
     *
     * <p>Never calls the provider on its own account: prices come from
     * {@link ProductPriceCache}, which decides when the provider is asked.</p>
     *
     * @param countryCode ISO 3166-1 alpha-2 code the request is attributed to, or {@code null}
     * @return the offers, grouped by currency, and the suggestion; never {@code null}
     */
    public PaymentOffersDto describeOffersFor(final String countryCode) {
        final List<PlanOffer> offers = listOffers();
        final Set<String> offeredCurrencies = offers.stream()
                .map(PlanOffer::currency)
                .collect(Collectors.toUnmodifiableSet());
        return paymentOffersMapper.toDto(new PaymentOffers(
                currencySuggestionPolicy.suggestCurrency(countryCode, offeredCurrencies), offers));
    }

    /**
     * Pairs every catalog product with its known price.
     *
     * @return the offers, in {@link #OFFER_ORDER}; empty when payments are off
     */
    private List<PlanOffer> listOffers() {
        if (!paymentProductCatalog.isAvailable()) {
            return List.of();
        }
        final Map<String, ProductPrice> knownPrices = productPriceCache.knownPricesByProductId();
        return paymentProductCatalog.listCatalogProducts().stream()
                .flatMap(catalogProduct -> offerOf(catalogProduct, knownPrices).stream())
                .sorted(OFFER_ORDER)
                .toList();
    }

    /**
     * Builds the offer for one product, in the currency the provider charges it in.
     *
     * @param catalogProduct the catalog entry
     * @param knownPrices    every known price
     * @return the offer, or empty when the product's price is not known
     */
    private static Optional<PlanOffer> offerOf(
            final CatalogProduct catalogProduct, final Map<String, ProductPrice> knownPrices) {

        return Optional.ofNullable(knownPrices.get(catalogProduct.productId()))
                .map(price -> new PlanOffer(catalogProduct.plan(), price.currency(),
                        price.amountMinorUnits(), price.billingPeriod()));
    }
}
