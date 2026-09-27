package com.kovospace.newtablinks.payment.config;

import java.net.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client this service calls Creem's API with.
 *
 * @since 0.0.9
 */
@Configuration
public class CreemRestClientConfiguration {

    /** Name of the bean, for the one place that injects it. */
    public static final String CREEM_REST_CLIENT = "creemRestClient";

    /** Header Creem reads the API key from. */
    private static final String API_KEY_HEADER = "x-api-key";

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CreemRestClientConfiguration.class);

    /**
     * Builds the client, pointed at the host the configured key belongs to.
     *
     * <p>With no key configured the client has no host and no key; nothing calls it then,
     * because the gateway refuses first.</p>
     *
     * @param creemProperties the Creem configuration
     * @return the client
     */
    @Bean(CREEM_REST_CLIENT)
    public RestClient creemRestClient(final CreemProperties creemProperties) {
        final JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(creemProperties.apiTimeout()).build());
        requestFactory.setReadTimeout(creemProperties.apiTimeout());

        final RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        creemProperties.apiMode().ifPresentOrElse(
                mode -> {
                    builder.baseUrl(mode.apiBaseUrl())
                            .defaultHeader(API_KEY_HEADER, creemProperties.apiKey());
                    LOGGER.info("Creem checkout is enabled in {} mode against {}",
                            mode, mode.apiBaseUrl());
                },
                () -> LOGGER.warn("No Creem API key is configured, so checkouts cannot be "
                        + "started. Set CREEM_API_KEY to enable them."));
        if (!creemProperties.isWebhookConfigured()) {
            LOGGER.warn("No Creem webhook secret is configured, so every webhook delivery will "
                    + "be refused. Set CREEM_WEBHOOK_SECRET to accept them.");
        }
        return builder.build();
    }
}
