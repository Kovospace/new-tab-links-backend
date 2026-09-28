package com.kovospace.newtablinks.statistics.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The primary key of {@link DailyMetricEntity}: one row per day and metric.
 *
 * <p>The metric is held as its stored identifier rather than as {@link UsageMetric}, because the
 * upsert that writes these rows is native SQL and binds the identifier directly.</p>
 *
 * @since 0.0.11
 */
@Embeddable
public class DailyMetricKey implements Serializable {

    /** The day counted, in UTC for server-side counts and the client's date for reported ones. */
    @Column(name = "day", nullable = false, updatable = false)
    private LocalDate countedOn;

    /** The {@link UsageMetric#getIdentifier() identifier} of the metric counted. */
    @Column(name = "metric", nullable = false, length = 40, updatable = false)
    private String metricIdentifier;

    /**
     * Required by JPA.
     */
    protected DailyMetricKey() {
    }

    /**
     * Returns the day counted.
     *
     * @return the day
     */
    public LocalDate getCountedOn() {
        return countedOn;
    }

    /**
     * Returns the identifier of the metric counted.
     *
     * @return the metric identifier, for example {@code new_tabs}
     */
    public String getMetricIdentifier() {
        return metricIdentifier;
    }

    /**
     * Compares by day and metric, as a composite key must.
     *
     * @param other the object to compare with
     * @return {@code true} when both name the same day and metric
     */
    @Override
    public boolean equals(final Object other) {
        return this == other
                || other instanceof DailyMetricKey key
                && Objects.equals(countedOn, key.countedOn)
                && Objects.equals(metricIdentifier, key.metricIdentifier);
    }

    /**
     * Hashes by day and metric, consistently with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(countedOn, metricIdentifier);
    }
}
