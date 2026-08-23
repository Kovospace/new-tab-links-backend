package com.kovospace.newtablinks.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sender identity for outgoing mail, bound from {@code newtablinks.mail.*}.
 *
 * <p>The transport itself is Spring's {@code spring.mail.*} configuration, so the transactional
 * provider is a matter of deployment configuration and never of code.</p>
 *
 * @param fromAddress address messages are sent from; must be one the provider has authorised,
 *                    or the message will be rejected or spam filed
 * @param fromName    display name shown beside the address
 * @param enabled     whether messages are actually transmitted; when {@code false} they are
 *                    logged instead, which is what makes local development possible without a
 *                    mail provider
 * @since 0.0.2
 */
@ConfigurationProperties(prefix = "newtablinks.mail")
public record MailProperties(String fromAddress, String fromName, boolean enabled) {
}
