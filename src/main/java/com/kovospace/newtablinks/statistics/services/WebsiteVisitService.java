package com.kovospace.newtablinks.statistics.services;

import com.kovospace.newtablinks.statistics.models.UsageMetric;
import com.kovospace.newtablinks.statistics.repositories.DailyMetricRepository;
import com.kovospace.newtablinks.statistics.utils.AutomatedUserAgentDetector;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts human visitors to the website, one for every visit reported.
 *
 * <p><strong>Deduplication is the website's job, not this service's.</strong> The page remembers
 * the day it last reported a visit and reports at most once a day. It used to be done here, by
 * a daily hash of address and browser, but behind carrier-grade NAT thousands of people share
 * one address, and browsers now send identical {@code User-Agent} strings - so a whole
 * neighbourhood collapsed into a handful of visitors. Nothing about the visitor is stored or
 * compared any more; the address is not even passed in.</p>
 *
 * <p>The day is the server's, in UTC.</p>
 *
 * @since 0.0.11
 */
@Service
public class WebsiteVisitService {

    private final DailyMetricRepository dailyMetricRepository;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param dailyMetricRepository stores the totals
     * @param usageStatisticsClock  supplies the day of the visit
     */
    public WebsiteVisitService(
            final DailyMetricRepository dailyMetricRepository,
            final Clock usageStatisticsClock) {

        this.dailyMetricRepository = dailyMetricRepository;
        this.clock = usageStatisticsClock;
    }

    /**
     * Counts a visit, unless the browser is missing or declares itself automated.
     *
     * @param userAgent the visitor's {@code User-Agent}; {@code null} when absent
     * @return {@code true} when the visit was counted
     */
    @Transactional
    public boolean recordVisit(final String userAgent) {
        if (AutomatedUserAgentDetector.isMissingOrAutomated(userAgent)) {
            return false;
        }
        dailyMetricRepository.addToDailyTotal(
                LocalDate.now(clock), UsageMetric.WEBSITE_VISITORS.getIdentifier(), 1);
        return true;
    }
}
