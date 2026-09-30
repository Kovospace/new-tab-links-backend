package com.kovospace.newtablinks.payment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.payment.config.PaymentPricingProperties;
import com.kovospace.newtablinks.payment.dtos.PaymentOffersDto;
import com.kovospace.newtablinks.payment.dtos.PlanOfferDto;
import com.kovospace.newtablinks.payment.mappers.PaymentOffersMapper;
import com.kovospace.newtablinks.payment.models.CatalogProduct;
import com.kovospace.newtablinks.payment.models.ProPlan;
import com.kovospace.newtablinks.payment.models.ProductPrice;
import java.time.Duration;
import java.time.Period;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the offers: the provider's currency and price, omission of unknown prices, and an empty
 * answer rather than an error when payments are off.
 *
 * @since 0.0.14
 */
class PaymentOfferServiceTest {

    private final PaymentProductCatalog catalog = mock(PaymentProductCatalog.class);
    private final ProductPriceCache cache = mock(ProductPriceCache.class);
    private final PaymentOfferService service = new PaymentOfferService(catalog, cache,
            new CurrencySuggestionPolicy(new PaymentPricingProperties("USD",
                    Map.of("EUR", List.of("SK")), Duration.ofHours(1), Duration.ofMinutes(5))),
            new PaymentOffersMapper());

    @Test
    @DisplayName("answers the default currency and no offers when payments are not configured")
    void shouldAnswerEmptyWhenNotConfigured() {
        when(catalog.isAvailable()).thenReturn(false);

        final PaymentOffersDto offers = service.describeOffersFor("SK");

        assertThat(offers.suggestedCurrency()).isEqualTo("USD");
        assertThat(offers.offers()).isEmpty();
        verify(cache, never()).knownPricesByProductId();
    }

    @Test
    @DisplayName("lists every priced product, subscription first per currency, in Creem's "
            + "currency, and suggests the country's currency")
    void shouldListPricedProductsInTheProvidersCurrency() {
        when(catalog.isAvailable()).thenReturn(true);
        when(catalog.listCatalogProducts()).thenReturn(List.of(
                new CatalogProduct("EUR", ProPlan.LIFETIME, "prod_eur_lifetime"),
                new CatalogProduct("EUR", ProPlan.SUBSCRIPTION, "prod_eur_yearly"),
                new CatalogProduct("USD", ProPlan.LIFETIME, "prod_usd_lifetime"),
                new CatalogProduct("USD", ProPlan.SUBSCRIPTION, "prod_usd_yearly")));
        when(cache.knownPricesByProductId()).thenReturn(Map.of(
                "prod_eur_lifetime", new ProductPrice("prod_eur_lifetime", "EUR", 1499, null),
                "prod_eur_yearly", new ProductPrice("prod_eur_yearly", "EUR", 468, Period.ofYears(1)),
                // Filed under USD but charged in CZK by Creem: Creem wins.
                "prod_usd_lifetime", new ProductPrice("prod_usd_lifetime", "CZK", 39900, null)));

        final PaymentOffersDto offers = service.describeOffersFor("SK");

        assertThat(offers.suggestedCurrency()).isEqualTo("EUR");
        assertThat(offers.offers()).containsExactly(
                new PlanOfferDto(ProPlan.LIFETIME, "CZK", 39900, null),
                new PlanOfferDto(ProPlan.SUBSCRIPTION, "EUR", 468, "P1Y"),
                new PlanOfferDto(ProPlan.LIFETIME, "EUR", 1499, null));
    }

    @Test
    @DisplayName("falls back to an offered currency when the country's has no known price")
    void shouldSuggestAnOfferedCurrency() {
        when(catalog.isAvailable()).thenReturn(true);
        when(catalog.listCatalogProducts()).thenReturn(List.of(
                new CatalogProduct("EUR", ProPlan.LIFETIME, "prod_eur_lifetime"),
                new CatalogProduct("USD", ProPlan.LIFETIME, "prod_usd_lifetime")));
        when(cache.knownPricesByProductId()).thenReturn(Map.of(
                "prod_usd_lifetime", new ProductPrice("prod_usd_lifetime", "USD", 1599, null)));

        final PaymentOffersDto offers = service.describeOffersFor("SK");

        assertThat(offers.suggestedCurrency()).isEqualTo("USD");
        assertThat(offers.offers()).extracting(PlanOfferDto::currency).containsExactly("USD");
        verify(catalog, never()).readPrice(any());
    }
}
