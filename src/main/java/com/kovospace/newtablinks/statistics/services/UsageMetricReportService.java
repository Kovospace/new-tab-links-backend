package com.kovospace.newtablinks.statistics.services;

import com.kovospace.newtablinks.statistics.dtos.UsageMetricDayDto;
import com.kovospace.newtablinks.statistics.dtos.UsageMetricMonthDto;
import com.kovospace.newtablinks.statistics.models.DailyMetricEntity;
import com.kovospace.newtablinks.statistics.models.UsageMetric;
import com.kovospace.newtablinks.statistics.repositories.DailyMetricRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a usage metric back for the admin page, one calendar month at a time.
 *
 * @since 0.0.11
 */
@Service
public class UsageMetricReportService {

    private final DailyMetricRepository dailyMetricRepository;

    /**
     * Creates the service.
     *
     * @param dailyMetricRepository reads the stored totals
     */
    public UsageMetricReportService(final DailyMetricRepository dailyMetricRepository) {
        this.dailyMetricRepository = dailyMetricRepository;
    }

    /**
     * Describes one metric over one month, with a value for every day.
     *
     * <p>Days nothing was counted on have no row and are returned as 0, so the graph needs no gap
     * handling. Only the requested metric is read; the metrics are never summed together.</p>
     *
     * @param metric the metric to read
     * @param month  the calendar month
     * @return every day of the month in order, and their sum
     */
    @Transactional(readOnly = true)
    public UsageMetricMonthDto describeMonth(final UsageMetric metric, final YearMonth month) {
        final Map<LocalDate, Long> storedTotalsByDay = readStoredTotalsByDay(metric, month);
        final List<UsageMetricDayDto> days = month.atDay(1)
                .datesUntil(month.plusMonths(1).atDay(1))
                .map(day -> new UsageMetricDayDto(day, storedTotalsByDay.getOrDefault(day, 0L)))
                .toList();
        final long total = days.stream().mapToLong(UsageMetricDayDto::value).sum();
        return new UsageMetricMonthDto(metric.getIdentifier(), month.toString(), days, total);
    }

    /**
     * Reads the rows that exist for a metric in a month, keyed by day.
     *
     * @param metric the metric to read
     * @param month  the calendar month
     * @return each stored day's total; days without a row are absent
     */
    private Map<LocalDate, Long> readStoredTotalsByDay(
            final UsageMetric metric, final YearMonth month) {

        return dailyMetricRepository
                .findByMetricBetweenDays(metric.getIdentifier(), month.atDay(1), month.atEndOfMonth())
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getKey().getCountedOn(), DailyMetricEntity::getTotal));
    }
}
