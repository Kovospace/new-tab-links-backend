package com.kovospace.newtablinks.statistics.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.statistics.dtos.UsageMetricDayDto;
import com.kovospace.newtablinks.statistics.dtos.UsageMetricMonthDto;
import com.kovospace.newtablinks.statistics.models.DailyMetricEntity;
import com.kovospace.newtablinks.statistics.models.DailyMetricKey;
import com.kovospace.newtablinks.statistics.models.UsageMetric;
import com.kovospace.newtablinks.statistics.repositories.DailyMetricRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks that a month is always returned whole, gaps filled with zero.
 *
 * @since 0.0.11
 */
class UsageMetricReportServiceTest {

    private final DailyMetricRepository dailyMetricRepository = mock(DailyMetricRepository.class);
    private final UsageMetricReportService usageMetricReportService =
            new UsageMetricReportService(dailyMetricRepository);

    @Test
    @DisplayName("returns every day of the month, zero where no row exists, and their sum")
    void shouldFillMissingDaysWithZero() {
        final List<DailyMetricEntity> storedRows = List.of(
                storedTotal(LocalDate.parse("2026-02-03"), 5),
                storedTotal(LocalDate.parse("2026-02-28"), 7));
        when(dailyMetricRepository.findByMetricBetweenDays(
                "website_visitors", LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28")))
                .thenReturn(storedRows);

        final UsageMetricMonthDto month = usageMetricReportService.describeMonth(
                UsageMetric.WEBSITE_VISITORS, YearMonth.parse("2026-02"));

        assertThat(month.metric()).isEqualTo("website_visitors");
        assertThat(month.month()).isEqualTo("2026-02");
        assertThat(month.days()).hasSize(28);
        assertThat(month.days().getFirst())
                .isEqualTo(new UsageMetricDayDto(LocalDate.parse("2026-02-01"), 0));
        assertThat(month.days().get(2))
                .isEqualTo(new UsageMetricDayDto(LocalDate.parse("2026-02-03"), 5));
        assertThat(month.days().getLast())
                .isEqualTo(new UsageMetricDayDto(LocalDate.parse("2026-02-28"), 7));
        assertThat(month.total()).isEqualTo(12);
    }

    @Test
    @DisplayName("returns a month with nothing counted as all zeros, not as empty")
    void shouldReturnAllZerosForEmptyMonth() {
        final UsageMetricMonthDto month = usageMetricReportService.describeMonth(
                UsageMetric.NEW_TABS, YearMonth.parse("2026-09"));

        assertThat(month.days()).hasSize(30).allMatch(day -> day.value() == 0);
        assertThat(month.total()).isZero();
    }

    /**
     * Stands in for a stored row.
     *
     * @param day   the day it totals
     * @param total its total
     * @return a mock entity answering both
     */
    private static DailyMetricEntity storedTotal(final LocalDate day, final long total) {
        final DailyMetricKey key = mock(DailyMetricKey.class);
        when(key.getCountedOn()).thenReturn(day);
        final DailyMetricEntity entity = mock(DailyMetricEntity.class);
        when(entity.getKey()).thenReturn(key);
        when(entity.getTotal()).thenReturn(total);
        return entity;
    }
}
