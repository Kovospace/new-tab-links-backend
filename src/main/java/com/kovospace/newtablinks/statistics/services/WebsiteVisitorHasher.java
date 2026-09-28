package com.kovospace.newtablinks.statistics.services;

import com.kovospace.newtablinks.statistics.config.UsageStatisticsProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Turns a website visitor into a value that recognises them again today and never afterwards.
 *
 * <p>An HMAC-SHA256 of address, browser and day under a server secret. The secret is what makes
 * it irreversible: without it, the small space of IPv4 addresses could be hashed exhaustively and
 * matched. The day inside the hash is what makes it unlinkable across days, even before the
 * nightly cleanup removes it.</p>
 *
 * <p>With no secret configured, one is generated at startup. That still hashes safely, but every
 * pod - and every restart - has a different key, so a visitor who reaches two pods in a day is
 * counted twice. Configure {@code STATS_VISITOR_HASH_SECRET} for exact counts.</p>
 *
 * @since 0.0.11
 */
@Component
public class WebsiteVisitorHasher {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebsiteVisitorHasher.class);

    private static final String ALGORITHM = "HmacSHA256";

    /** Length of a generated key; the size of the hash's own output. */
    private static final int GENERATED_KEY_LENGTH_IN_BYTES = 32;

    /** Separates the hashed fields, so that no two different inputs concatenate alike. */
    private static final char FIELD_SEPARATOR = '|';

    private final SecretKeySpec hashKey;

    /**
     * Creates the hasher, generating a per-process key when none is configured.
     *
     * @param usageStatisticsProperties supplies the configured secret, if any
     */
    public WebsiteVisitorHasher(final UsageStatisticsProperties usageStatisticsProperties) {
        this.hashKey = new SecretKeySpec(resolveKey(usageStatisticsProperties), ALGORITHM);
    }

    /**
     * Hashes one visitor for one day.
     *
     * @param clientAddress the visitor's address as resolved behind the ingress
     * @param userAgent     the visitor's {@code User-Agent}
     * @param day           the day of the visit, in UTC
     * @return the 32-byte hash
     */
    public byte[] hashVisitor(
            final String clientAddress, final String userAgent, final LocalDate day) {

        final String visitor = clientAddress + FIELD_SEPARATOR + userAgent + FIELD_SEPARATOR + day;
        try {
            final Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(hashKey);
            return mac.doFinal(visitor.getBytes(StandardCharsets.UTF_8));
        } catch (final NoSuchAlgorithmException | InvalidKeyException unavailable) {
            // HmacSHA256 is mandatory on every Java platform, and any non-empty key is valid.
            throw new IllegalStateException("HMAC-SHA256 is unavailable", unavailable);
        }
    }

    /**
     * Returns the configured key, or a random one with a warning when none is configured.
     *
     * @param usageStatisticsProperties supplies the configured secret, if any
     * @return the key bytes
     */
    private static byte[] resolveKey(final UsageStatisticsProperties usageStatisticsProperties) {
        if (usageStatisticsProperties.isVisitorHashSecretConfigured()) {
            return usageStatisticsProperties.visitorHashSecret().getBytes(StandardCharsets.UTF_8);
        }
        LOGGER.warn("No visitor hash secret is configured, so a random one was generated for this "
                + "process. Website visitors are then deduplicated per pod and per restart only. "
                + "Set STATS_VISITOR_HASH_SECRET for exact counts.");
        final byte[] generatedKey = new byte[GENERATED_KEY_LENGTH_IN_BYTES];
        new SecureRandom().nextBytes(generatedKey);
        return generatedKey;
    }
}
