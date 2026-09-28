package com.kovospace.newtablinks.statistics.services;

import com.kovospace.newtablinks.statistics.config.UsageStatisticsProperties;
import com.kovospace.newtablinks.statistics.dtos.NewTabDayCountDto;
import com.kovospace.newtablinks.statistics.dtos.NewTabReportRequestDto;
import com.kovospace.newtablinks.statistics.dtos.UsageReportAcknowledgementDto;
import com.kovospace.newtablinks.statistics.models.UsageMetric;
import com.kovospace.newtablinks.statistics.repositories.DailyMetricRepository;
import com.kovospace.newtablinks.statistics.utils.NewTabDayCountAcceptancePolicy;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds the extension's reported new-tab counts to the daily totals.
 *
 * <p>Anonymous by construction: a report carries days and counts and nothing else, and this
 * service is never told who sent it. Each accepted entry is added to its day as reported, in the
 * client's local date, because that is the day the tabs were opened for the person who opened
 * them.</p>
 *
 * @since 0.0.11
 */
@Service
public class NewTabReportService {

    private final DailyMetricRepository dailyMetricRepository;
    private final UsageStatisticsProperties usageStatisticsProperties;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param dailyMetricRepository     stores the totals
     * @param usageStatisticsProperties supplies the interval the acknowledgement carries
     * @param usageStatisticsClock      supplies the server's date the report is judged against
     */
    public NewTabReportService(
            final DailyMetricRepository dailyMetricRepository,
            final UsageStatisticsProperties usageStatisticsProperties,
            final Clock usageStatisticsClock) {

        this.dailyMetricRepository = dailyMetricRepository;
        this.usageStatisticsProperties = usageStatisticsProperties;
        this.clock = usageStatisticsClock;
    }

    /**
     * Adds every acceptable entry of a report to its day's total.
     *
     * <p>Entries outside {@link NewTabDayCountAcceptancePolicy} are dropped silently, and the
     * report still succeeds - the extension discards what was acknowledged, so an error would
     * leave an impossible entry to be resent forever. Two entries for one day are both added.
     * All or nothing: one transaction for the whole report.</p>
     *
     * @param report the validated report
     * @return when the extension should report next
     */
    @Transactional
    public UsageReportAcknowledgementDto recordReport(final NewTabReportRequestDto report) {
        final LocalDate today = LocalDate.now(clock);
        for (final NewTabDayCountDto dayCount : report.days()) {
            if (NewTabDayCountAcceptancePolicy.isAccepted(dayCount, today)) {
                dailyMetricRepository.addToDailyTotal(
                        dayCount.day(), UsageMetric.NEW_TABS.getIdentifier(), dayCount.count());
            }
        }
        return new UsageReportAcknowledgementDto(usageStatisticsProperties.reportIntervalSeconds());
    }
}
