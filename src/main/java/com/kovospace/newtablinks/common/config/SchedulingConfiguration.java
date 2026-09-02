package com.kovospace.newtablinks.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches on Spring's scheduler, so that {@code @Scheduled} methods actually run.
 *
 * <p>Declared as its own configuration rather than as another annotation on the application
 * class: background work that runs on a timer is worth being able to find, and this is where
 * anything that has to know "does this deployment run scheduled jobs?" can look.</p>
 *
 * <p>The scheduler runs in every replica. Every job in this application must therefore be safe to
 * run concurrently with itself on another pod - there is no leader election here.</p>
 *
 * @since 0.0.5
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
