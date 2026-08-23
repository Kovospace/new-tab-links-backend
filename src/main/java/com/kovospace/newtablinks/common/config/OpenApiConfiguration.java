package com.kovospace.newtablinks.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the document-level metadata of the generated OpenAPI description.
 *
 * <p>Endpoint and schema level documentation is written as annotations next to the code it
 * describes; only what applies to the document as a whole belongs here.</p>
 *
 * @since 0.0.1
 */
@Configuration
public class OpenApiConfiguration {

    private final String applicationName;
    private final String applicationVersion;
    private final String applicationDescription;

    /**
     * Creates the configuration from values declared in {@code application.properties}.
     *
     * @param applicationName        public name of the API
     * @param applicationVersion     version of the API contract
     * @param applicationDescription short description of what the API offers
     */
    public OpenApiConfiguration(
            @Value("${newtablinks.openapi.title}") final String applicationName,
            @Value("${newtablinks.openapi.version}") final String applicationVersion,
            @Value("${newtablinks.openapi.description}") final String applicationDescription) {

        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
        this.applicationDescription = applicationDescription;
    }

    /**
     * Builds the OpenAPI document metadata served at {@code /v3/api-docs} and rendered by Swagger UI.
     *
     * @return the OpenAPI document description
     */
    @Bean
    public OpenAPI newTabLinksOpenApiDefinition() {
        return new OpenAPI().info(new Info()
                .title(applicationName)
                .version(applicationVersion)
                .description(applicationDescription)
                .contact(new Contact().name("Kovospace"))
                .license(new License().name("Proprietary")));
    }
}
