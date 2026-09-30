package com.kovospace.newtablinks.payment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.common.exceptions.PaymentProviderRequestFailedException;
import com.kovospace.newtablinks.payment.config.PaymentPricingProperties;
import com.kovospace.newtablinks.payment.models.CatalogProduct;
import com.kovospace.newtablinks.payment.models.ProPlan;
import com.kovospace.newtablinks.payment.models.ProductPrice;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that prices are read from the provider once per lifetime however often they are asked
 * for, and that an outage keeps the last known price instead of losing it.
 *
 * @since 0.0.14
 */
class ProductPriceCacheTest {

    private static final Instant START = Instant.parse("2026-09-30T10:00:00Z");
    private static final ProductPrice EUR_YEARLY =
            new ProductPrice("prod_eur_yearly", "EUR", 468, Period.ofYears(1));
    private static final ProductPrice USD_LIFETIME =
            new ProductPrice("prod_usd_lifetime", "USD", 1599, null);
    private static final PaymentProviderRequestFailedException CREEM_DOWN =
            new PaymentProviderRequestFailedException("down", null);

    private final PaymentProductCatalog catalog = mock(PaymentProductCatalog.class);
    private final Clock clock = mock(Clock.class);
    private final ProductPriceCache cache = new ProductPriceCache(catalog,
            new PaymentPricingProperties("USD", Map.of(), Duration.ofHours(1),
                    Duration.ofMinutes(5)),
            clock);

    /** Two products on sale, both readable, at the start time. */
    @BeforeEach
    void configureCatalog() {
        when(clock.instant()).thenReturn(START);
        when(catalog.listCatalogProducts()).thenReturn(List.of(
                new CatalogProduct("EUR", ProPlan.SUBSCRIPTION, "prod_eur_yearly"),
                new CatalogProduct("USD", ProPlan.LIFETIME, "prod_usd_lifetime")));
        when(catalog.readPrice("prod_eur_yearly")).thenReturn(EUR_YEARLY);
        when(catalog.readPrice("prod_usd_lifetime")).thenReturn(USD_LIFETIME);
    }

    @Test
    @DisplayName("asks the provider once per lifetime, however many requests arrive")
    void shouldReadEachPriceOncePerLifetime() {
        for (int request = 0; request < 50; request++) {
            assertThat(cache.knownPricesByProductId()).containsOnlyKeys(
                    "prod_eur_yearly", "prod_usd_lifetime");
        }
        when(clock.instant()).thenReturn(START.plus(Duration.ofMinutes(59)));
        cache.knownPricesByProductId();
        verify(catalog, times(1)).readPrice("prod_eur_yearly");

        when(clock.instant()).thenReturn(START.plus(Duration.ofHours(1)));
        cache.knownPricesByProductId();
        verify(catalog, times(2)).readPrice("prod_eur_yearly");
    }

    @Test
    @DisplayName("keeps serving the last known price while the provider is down")
    void shouldKeepTheLastKnownPriceWhenARefreshFails() {
        cache.knownPricesByProductId();
        doThrow(CREEM_DOWN).when(catalog).readPrice("prod_eur_yearly");
        when(clock.instant()).thenReturn(START.plus(Duration.ofHours(2)));

        assertThat(cache.knownPricesByProductId())
                .containsEntry("prod_eur_yearly", EUR_YEARLY)
                .containsEntry("prod_usd_lifetime", USD_LIFETIME);
    }

    @Test
    @DisplayName("leaves out a price never read, and retries after the short interval")
    void shouldOmitANeverReadPriceAndRetrySoon() {
        doThrow(CREEM_DOWN).when(catalog).readPrice("prod_usd_lifetime");

        assertThat(cache.knownPricesByProductId()).containsOnlyKeys("prod_eur_yearly");

        when(clock.instant()).thenReturn(START.plus(Duration.ofMinutes(4)));
        cache.knownPricesByProductId();
        verify(catalog, times(1)).readPrice("prod_usd_lifetime");

        when(clock.instant()).thenReturn(START.plus(Duration.ofMinutes(5)));
        doReturn(USD_LIFETIME).when(catalog).readPrice("prod_usd_lifetime");
        assertThat(cache.knownPricesByProductId()).containsEntry("prod_usd_lifetime", USD_LIFETIME);
    }
}
