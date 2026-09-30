package com.kovospace.newtablinks.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kovospace.newtablinks.payment.models.CatalogProduct;
import com.kovospace.newtablinks.payment.models.ProPlan;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Binds the product catalog and the country rules from the real {@code application.properties},
 * with environment variables standing in for a deployment - so the variable names the deployment
 * sets are tested, not just the Java behind them.
 *
 * @since 0.0.14
 */
class PaymentCatalogBindingTest {

    private static final String TEST_KEY = "creem_test_2B9wYk1bXQ3xR8";

    @Test
    @DisplayName("keeps the original variables as the EUR products and adds the _USD ones")
    void shouldBindEuroFromTheOriginalVariablesAndUsdFromTheSuffixedOnes() throws IOException {
        final CreemProperties creem = bindCreem(Map.of(
                "CREEM_API_KEY", TEST_KEY,
                "CREEM_LIFETIME_PRODUCT_ID", "prod_eur_lifetime",
                "CREEM_SUBSCRIPTION_PRODUCT_ID", "prod_eur_yearly",
                "CREEM_LIFETIME_PRODUCT_ID_USD", "prod_usd_lifetime",
                "CREEM_SUBSCRIPTION_PRODUCT_ID_USD", "prod_usd_yearly"));

        assertThat(creem.catalogProducts()).containsExactly(
                new CatalogProduct("EUR", ProPlan.LIFETIME, "prod_eur_lifetime"),
                new CatalogProduct("EUR", ProPlan.SUBSCRIPTION, "prod_eur_yearly"),
                new CatalogProduct("USD", ProPlan.LIFETIME, "prod_usd_lifetime"),
                new CatalogProduct("USD", ProPlan.SUBSCRIPTION, "prod_usd_yearly"));
        assertThat(creem.productIdFor(ProPlan.SUBSCRIPTION, "usd")).contains("prod_usd_yearly");
    }

    @Test
    @DisplayName("offers no currency whose products are both blank")
    void shouldLeaveOutACurrencyWithNoProducts() throws IOException {
        final CreemProperties creem = bindCreem(Map.of(
                "CREEM_API_KEY", TEST_KEY,
                "CREEM_LIFETIME_PRODUCT_ID_USD", "prod_usd_lifetime"));

        assertThat(creem.catalogProducts()).containsExactly(
                new CatalogProduct("USD", ProPlan.LIFETIME, "prod_usd_lifetime"));
        assertThat(creem.productIdFor(ProPlan.LIFETIME, "EUR")).isEmpty();
        assertThat(creem.productIdFor(ProPlan.SUBSCRIPTION, "USD")).isEmpty();
    }

    @Test
    @DisplayName("adds a currency from relaxed-binding environment variables alone, no code")
    void shouldAddACurrencyThroughEnvironmentVariablesAlone() throws IOException {
        final CreemProperties creem = bindCreem(Map.of(
                "NEWTABLINKS_PAYMENT_CREEM_PRODUCTS_GBP_LIFETIME", "prod_gbp_lifetime",
                "NEWTABLINKS_PAYMENT_CREEM_PRODUCTS_GBP_SUBSCRIPTION", "prod_gbp_yearly"));
        final PaymentPricingProperties pricing = bindPricing(Map.of(
                "NEWTABLINKS_PAYMENT_PRICING_COUNTRIES_GBP", "GG,JE,IM"));

        assertThat(creem.productIdFor(ProPlan.LIFETIME, "GBP")).contains("prod_gbp_lifetime");
        assertThat(creem.productIdFor(ProPlan.SUBSCRIPTION, "GBP")).contains("prod_gbp_yearly");
        assertThat(pricing.currencyForCountry("JE")).contains("GBP");
        assertThat(pricing.currencyForCountry("SK")).contains("EUR");
    }

    @Test
    @DisplayName("binds USD as the default, and to EUR the EU, the EEA, the other euro "
            + "countries, GB and CH")
    void shouldBindTheShippedCountryRules() throws IOException {
        final PaymentPricingProperties pricing = bindPricing(Map.of());

        assertThat(pricing.defaultCurrency()).isEqualTo("USD");
        assertThat(pricing.countries().get("EUR")).hasSize(38)
                .contains("AT", "SK", "SE", "IS", "LI", "NO", "AD", "MC", "SM", "VA", "ME", "XK",
                        "GB", "CH")
                .doesNotContain("US", "CA");
        assertThat(pricing.priceCacheLifetime()).isEqualTo(Duration.ofHours(1));
        assertThat(pricing.priceRefreshRetryInterval()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("refuses to start with a catalog key that is not a currency code")
    void shouldRefuseACatalogKeyThatIsNotACurrency() {
        assertThatThrownBy(() -> bindCreem(Map.of(
                "NEWTABLINKS_PAYMENT_CREEM_PRODUCTS_EURO_LIFETIME", "prod_x")))
                .isInstanceOf(BindException.class)
                .rootCause().hasMessageContaining("three-letter");
    }

    /**
     * Binds the Creem properties the way the application does.
     *
     * @param environmentVariables what the deployment sets
     * @return the bound properties
     * @throws IOException when {@code application.properties} cannot be read
     */
    private static CreemProperties bindCreem(final Map<String, Object> environmentVariables)
            throws IOException {

        return binderFor(environmentVariables)
                .bind("newtablinks.payment.creem", CreemProperties.class).get();
    }

    /**
     * Binds the pricing properties the way the application does.
     *
     * @param environmentVariables what the deployment sets
     * @return the bound properties
     * @throws IOException when {@code application.properties} cannot be read
     */
    private static PaymentPricingProperties bindPricing(
            final Map<String, Object> environmentVariables) throws IOException {

        return binderFor(environmentVariables)
                .bind("newtablinks.payment.pricing", PaymentPricingProperties.class).get();
    }

    /**
     * Builds a binder over {@code application.properties} and a stand-in for the environment,
     * which takes precedence as the real one does.
     *
     * @param environmentVariables what the deployment sets
     * @return the binder, resolving {@code ${...}} placeholders
     * @throws IOException when {@code application.properties} cannot be read
     */
    private static Binder binderFor(final Map<String, Object> environmentVariables)
            throws IOException {

        final StandardEnvironment environment = new StandardEnvironment();
        final MutablePropertySources sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environmentVariables));
        final List<PropertySource<?>> applicationProperties = new PropertiesPropertySourceLoader()
                .load("application.properties", new ClassPathResource("application.properties"));
        applicationProperties.forEach(sources::addLast);
        return Binder.get(environment);
    }
}
