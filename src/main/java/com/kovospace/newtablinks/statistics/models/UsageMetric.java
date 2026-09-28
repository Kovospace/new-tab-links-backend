package com.kovospace.newtablinks.statistics.models;

import java.util.Arrays;
import java.util.Optional;

/**
 * The things counted per day, each stored under its own identifier in {@code daily_metric}.
 *
 * <p>The identifiers are fixed: the migrated schema lists them in a {@code CHECK} constraint,
 * and the admin page asks for them by name. Adding a constant here without a migration makes
 * every insert of it fail at runtime - {@code ddl-auto=validate} does not see the constraint.</p>
 *
 * <p>The metrics are never added together; each admin graph reads only its own.</p>
 *
 * <p>A request parameter such as {@code metric=new_tabs} binds to a constant through
 * {@link com.kovospace.newtablinks.statistics.utils.UsageMetricIdentifierConverter}.</p>
 *
 * @since 0.0.11
 */
public enum UsageMetric {

    /** New tabs opened in the extension, as reported by the extension. */
    NEW_TABS("new_tabs"),

    /** Distinct human visitors to the website, each counted once per day. */
    WEBSITE_VISITORS("website_visitors");

    private final String identifier;

    /**
     * Creates the constant.
     *
     * @param identifier the value stored in {@code daily_metric.metric} and used by the API
     */
    UsageMetric(final String identifier) {
        this.identifier = identifier;
    }

    /**
     * Returns the value stored in the database and used on the API.
     *
     * @return the metric's identifier, for example {@code new_tabs}
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Finds the metric an identifier names.
     *
     * @param identifier the identifier as sent by a caller; case-sensitive
     * @return the metric, or empty when the identifier names none
     */
    public static Optional<UsageMetric> fromIdentifier(final String identifier) {
        return Arrays.stream(values())
                .filter(metric -> metric.identifier.equals(identifier))
                .findFirst();
    }
}
