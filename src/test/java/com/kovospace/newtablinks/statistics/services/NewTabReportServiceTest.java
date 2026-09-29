package com.kovospace.newtablinks.statistics.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kovospace.newtablinks.statistics.config.UsageStatisticsProperties;
import com.kovospace.newtablinks.statistics.dtos.NewTabDayCountDto;
import com.kovospace.newtablinks.statistics.dtos.NewTabReportRequestDto;
import com.kovospace.newtablinks.statistics.repositories.DailyMetricRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks which reported entries reach the totals, and what the acknowledgement carries.
 *
 * @since 0.0.11
 */
class NewTabReportServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-09-28");
    private static final int CONFIGURED_INTERVAL_SECONDS = 1234;

    private final DailyMetricRepository dailyMetricRepository = mock(DailyMetricRepository.class);
    private final NewTabReportService newTabReportService = new NewTabReportService(
            dailyMetricRepository,
            new UsageStatisticsProperties(
                    CONFIGURED_INTERVAL_SECONDS, 120, 5000, Duration.ofHours(1)),
            Clock.fixed(Instant.parse("2026-09-28T23:30:00Z"), ZoneOffset.UTC));

    @Test
    @DisplayName("adds every accepted entry to its own day under new_tabs")
    void shouldAddEveryAcceptedEntry() {
        newTabReportService.recordReport(report(
                new NewTabDayCountDto(TODAY, 12L),
                new NewTabDayCountDto(TODAY.plusDays(1), 3L),
                new NewTabDayCountDto(TODAY.minusDays(7), 2000L)));

        verify(dailyMetricRepository).addToDailyTotal(TODAY, "new_tabs", 12L);
        verify(dailyMetricRepository).addToDailyTotal(TODAY.plusDays(1), "new_tabs", 3L);
        verify(dailyMetricRepository).addToDailyTotal(TODAY.minusDays(7), "new_tabs", 2000L);
    }

    @Test
    @DisplayName("drops out-of-range days and counts without failing the report")
    void shouldDropOutOfRangeEntriesSilently() {
        final var acknowledgement = newTabReportService.recordReport(report(
                new NewTabDayCountDto(TODAY.minusDays(8), 5L),
                new NewTabDayCountDto(TODAY.plusDays(2), 5L),
                new NewTabDayCountDto(TODAY, 0L),
                new NewTabDayCountDto(TODAY, 2001L)));

        verify(dailyMetricRepository, never()).addToDailyTotal(any(), anyString(), anyLong());
        assertThat(acknowledgement.nextReportAfterSeconds()).isEqualTo(CONFIGURED_INTERVAL_SECONDS);
    }

    @Test
    @DisplayName("answers with the configured reporting interval")
    void shouldAnswerWithConfiguredInterval() {
        assertThat(newTabReportService.recordReport(report()).nextReportAfterSeconds())
                .isEqualTo(CONFIGURED_INTERVAL_SECONDS);
    }

    /**
     * Builds a report of the given entries.
     *
     * @param dayCounts the entries
     * @return the report
     */
    private static NewTabReportRequestDto report(final NewTabDayCountDto... dayCounts) {
        return new NewTabReportRequestDto(List.of(dayCounts));
    }
}
