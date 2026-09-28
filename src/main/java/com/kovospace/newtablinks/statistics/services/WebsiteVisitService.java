package com.kovospace.newtablinks.statistics.services;

import com.kovospace.newtablinks.statistics.models.UsageMetric;
import com.kovospace.newtablinks.statistics.repositories.DailyMetricRepository;
import com.kovospace.newtablinks.statistics.repositories.WebsiteVisitorHashRepository;
import com.kovospace.newtablinks.statistics.utils.AutomatedUserAgentDetector;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts distinct human visitors to the website, once each per day, without a cookie.
 *
 * <p>A visit is reduced to {@link WebsiteVisitorHasher a keyed hash} before anything is stored,
 * and the address it came from is never written or logged. The day is the server's, in UTC.</p>
 *
 * @since 0.0.11
 */
@Service
public class WebsiteVisitService {

    private final WebsiteVisitorHashRepository websiteVisitorHashRepository;
    private final DailyMetricRepository dailyMetricRepository;
    private final WebsiteVisitorHasher websiteVisitorHasher;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param websiteVisitorHashRepository remembers who was counted today
     * @param dailyMetricRepository        stores the totals
     * @param websiteVisitorHasher         reduces a visitor to today's hash
     * @param usageStatisticsClock         supplies the day of the visit
     */
    public WebsiteVisitService(
            final WebsiteVisitorHashRepository websiteVisitorHashRepository,
            final DailyMetricRepository dailyMetricRepository,
            final WebsiteVisitorHasher websiteVisitorHasher,
            final Clock usageStatisticsClock) {

        this.websiteVisitorHashRepository = websiteVisitorHashRepository;
        this.dailyMetricRepository = dailyMetricRepository;
        this.websiteVisitorHasher = websiteVisitorHasher;
        this.clock = usageStatisticsClock;
    }

    /**
     * Counts a visit, unless it is automated or the visitor was already counted today.
     *
     * <p>The hash insert and the total's increment share one transaction, so a visitor is never
     * remembered without being counted, nor counted twice.</p>
     *
     * @param clientAddress the visitor's address as resolved behind the ingress
     * @param userAgent     the visitor's {@code User-Agent}; {@code null} when absent
     * @return {@code true} when the visit was counted
     */
    @Transactional
    public boolean recordVisit(final String clientAddress, final String userAgent) {
        if (AutomatedUserAgentDetector.isMissingOrAutomated(userAgent)) {
            return false;
        }
        final LocalDate today = LocalDate.now(clock);
        final byte[] visitorHash = websiteVisitorHasher.hashVisitor(clientAddress, userAgent, today);
        final boolean isFirstVisitToday =
                websiteVisitorHashRepository.insertUnlessAlreadyRecorded(today, visitorHash) == 1;
        if (isFirstVisitToday) {
            dailyMetricRepository.addToDailyTotal(
                    today, UsageMetric.WEBSITE_VISITORS.getIdentifier(), 1);
        }
        return isFirstVisitToday;
    }

    /**
     * Deletes every visitor hash from before today.
     *
     * <p>A single statement judged by date, so safe on several replicas at once.</p>
     *
     * @return how many rows were deleted
     */
    @Transactional
    public int forgetVisitorsBeforeToday() {
        return websiteVisitorHashRepository.deleteRecordedBefore(LocalDate.now(clock));
    }
}
