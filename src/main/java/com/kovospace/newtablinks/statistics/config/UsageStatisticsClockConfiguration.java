package com.kovospace.newtablinks.statistics.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the clock that decides which day a usage count belongs to.
 *
 * <p>A bean rather than {@code LocalDate.now()} scattered through the services, so that the day
 * boundary - the thing every acceptance window and the nightly cleanup depend on - is one
 * decision, made in UTC, and replaceable in a test.</p>
 *
 * @since 0.0.11
 */
@Configuration
public class UsageStatisticsClockConfiguration {

    /**
     * Supplies the system clock in UTC.
     *
     * @return the clock the statistics module reads "today" from
     */
    @Bean
    public Clock usageStatisticsClock() {
        return Clock.systemUTC();
    }
}
