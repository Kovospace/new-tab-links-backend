package com.kovospace.newtablinks.statistics.repositories;

import com.kovospace.newtablinks.statistics.models.DailyMetricEntity;
import com.kovospace.newtablinks.statistics.models.DailyMetricKey;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence of the per-day usage totals.
 *
 * @since 0.0.11
 */
public interface DailyMetricRepository extends JpaRepository<DailyMetricEntity, DailyMetricKey> {

    /**
     * Adds to a day's total, creating the row when the day has none yet.
     *
     * <p>A single statement, so two pods adding to the same day at the same moment both land:
     * the primary key turns the second insert into an update of the first, and neither reads the
     * total before writing it.</p>
     *
     * @param countedOn        the day to add to
     * @param metricIdentifier the metric's stored identifier
     * @param increment        how much to add; positive
     * @return the number of rows written, always 1
     */
    @Modifying
    @Query(value = "INSERT INTO daily_metric (day, metric, value) "
            + "VALUES (:countedOn, :metricIdentifier, :increment) "
            + "ON CONFLICT (day, metric) DO UPDATE SET value = daily_metric.value + EXCLUDED.value",
            nativeQuery = true)
    int addToDailyTotal(
            @Param("countedOn") LocalDate countedOn,
            @Param("metricIdentifier") String metricIdentifier,
            @Param("increment") long increment);

    /**
     * Reads the stored totals of one metric over an inclusive range of days.
     *
     * @param metricIdentifier the metric's stored identifier
     * @param firstDay         the first day to include
     * @param lastDay          the last day to include
     * @return the rows that exist, in no particular order; days never counted have none
     */
    @Query("select metric from DailyMetricEntity metric "
            + "where metric.key.metricIdentifier = :metricIdentifier "
            + "and metric.key.countedOn between :firstDay and :lastDay")
    List<DailyMetricEntity> findByMetricBetweenDays(
            @Param("metricIdentifier") String metricIdentifier,
            @Param("firstDay") LocalDate firstDay,
            @Param("lastDay") LocalDate lastDay);
}
