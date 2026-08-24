package com.kovospace.newtablinks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the NewTabLinks backend service.
 *
 * @since 0.0.1
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NewTabLinksBackendApplication {

    /**
     * Starts the application.
     *
     * @param args command line arguments passed through to Spring Boot
     */
    public static void main(final String[] args) {
        SpringApplication.run(NewTabLinksBackendApplication.class, args);
    }
}
