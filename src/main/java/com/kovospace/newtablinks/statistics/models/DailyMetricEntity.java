package com.kovospace.newtablinks.statistics.models;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One day's total of one {@link UsageMetric}; week and month totals are sums of these rows.
 *
 * <p><strong>Does not extend {@code AbstractAuditableEntity}</strong>, the one exception in the
 * application, because the table has none of its columns: it is keyed by day and metric, and an
 * aggregate has no creator and no meaningful update time. Rows are written only by the native
 * upsert in
 * {@link com.kovospace.newtablinks.statistics.repositories.DailyMetricRepository}, never through
 * this class, which exists so that {@code ddl-auto=validate} checks the table and the admin read
 * can be a plain query.</p>
 *
 * @since 0.0.11
 */
@Entity
@Table(name = "daily_metric")
public class DailyMetricEntity {

    /** The day and metric this row totals. */
    @EmbeddedId
    private DailyMetricKey key;

    /** The day's total; never negative. */
    @Column(name = "value", nullable = false)
    private long total;

    /**
     * Required by JPA.
     */
    protected DailyMetricEntity() {
    }

    /**
     * Returns the day and metric this row totals.
     *
     * @return the composite key
     */
    public DailyMetricKey getKey() {
        return key;
    }

    /**
     * Returns the day's total.
     *
     * @return the total, zero or more
     */
    public long getTotal() {
        return total;
    }
}
