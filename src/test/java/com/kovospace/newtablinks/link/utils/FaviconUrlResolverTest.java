package com.kovospace.newtablinks.link.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies how {@link FaviconUrlResolver} chooses the favicon address to store.
 *
 * @since 0.0.1
 */
class FaviconUrlResolverTest {

    @Test
    @DisplayName("an address supplied by the client is kept as it is")
    void shouldKeepTheFaviconAddressSuppliedByTheClient() {
        assertThat(FaviconUrlResolver.resolveFaviconUrl(
                "https://cdn.example.com/icon.png", "https://example.com/page"))
                .isEqualTo("https://cdn.example.com/icon.png");
    }

    @Test
    @DisplayName("a missing address falls back to the site's conventional icon")
    void shouldDeriveTheConventionalIconWhenTheClientSuppliedNothing() {
        assertThat(FaviconUrlResolver.resolveFaviconUrl(null, "https://example.com/deep/page?q=1"))
                .isEqualTo("https://example.com/favicon.ico");
    }

    @Test
    @DisplayName("a blank address is treated the same as a missing one")
    void shouldTreatABlankFaviconAddressAsMissing() {
        assertThat(FaviconUrlResolver.resolveFaviconUrl("   ", "https://example.com"))
                .isEqualTo("https://example.com/favicon.ico");
    }

    @Test
    @DisplayName("a non standard port is preserved in the derived address")
    void shouldPreserveThePortWhenDerivingTheConventionalIcon() {
        assertThat(FaviconUrlResolver.resolveFaviconUrl(null, "http://localhost:8080/app"))
                .isEqualTo("http://localhost:8080/favicon.ico");
    }

    @Test
    @DisplayName("nothing is derived from an address that is not an absolute URL")
    void shouldDeriveNothingWhenTheLinkAddressIsNotAbsolute() {
        assertThat(FaviconUrlResolver.resolveFaviconUrl(null, "not a url")).isNull();
    }
}
