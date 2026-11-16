package com.kovospace.newtablinks.fairuse.services;

import com.kovospace.newtablinks.common.config.PlanLimitProperties;
import com.kovospace.newtablinks.common.models.PlanLimitValues;
import com.kovospace.newtablinks.fairuse.models.FairUseLimitsEntity;
import com.kovospace.newtablinks.fairuse.repositories.FairUseLimitsRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The global Fair Use Policy limits in force - what every account gets, before any per-account
 * raise and before the free plan's own lower numbers.
 *
 * <p>They are the one {@code fair_use_limits} row, which the operator edits. Every limit check
 * asks for them, so each replica keeps the row it read for
 * {@link PlanLimitProperties#fairUseCacheLifetime()}; a change made through another replica
 * reaches this one within that time, and nothing is broadcast between replicas. A change made
 * through this replica is visible here as soon as it commits.</p>
 *
 * <p>While the row is missing - it is seeded by the migration, so only on a database built some
 * other way - the configured {@code newtablinks.plan-limits.premium.*} values hold.</p>
 *
 * @since 0.0.20
 */
@Service
public class GlobalFairUseLimitsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalFairUseLimitsService.class);

    private final FairUseLimitsRepository fairUseLimitsRepository;
    private final PlanLimitProperties planLimitProperties;

    /** The limits last read, and when; {@code null} until the first read or after a change. */
    private volatile CachedFairUseLimits cachedFairUseLimits;

    /**
     * Creates the service.
     *
     * @param fairUseLimitsRepository persistence for the global row
     * @param planLimitProperties     the fallback values and the cache lifetime
     */
    public GlobalFairUseLimitsService(
            final FairUseLimitsRepository fairUseLimitsRepository,
            final PlanLimitProperties planLimitProperties) {

        this.fairUseLimitsRepository = fairUseLimitsRepository;
        this.planLimitProperties = planLimitProperties;
    }

    /**
     * Returns the global Fair Use limits in force, from this replica's cache while it is fresh.
     *
     * <p>Not transactional: a cache hit touches no database, and a miss is one primary-key read
     * that joins the caller's transaction when there is one.</p>
     *
     * @return the global Fair Use limits
     */
    public PlanLimitValues currentFairUseLimits() {
        final CachedFairUseLimits cached = cachedFairUseLimits;
        final Instant now = Instant.now();
        if (cached != null && cached.isFreshAt(now)) {
            return cached.limits();
        }
        final PlanLimitValues storedLimits = readStoredOrConfiguredLimits();
        cachedFairUseLimits = new CachedFairUseLimits(
                storedLimits, now.plus(planLimitProperties.fairUseCacheLifetime()));
        return storedLimits;
    }

    /**
     * Replaces the global Fair Use limits.
     *
     * <p>Lowering is allowed: nothing already stored is deleted by a limit, only growth past it is
     * refused. This replica forgets its cached copy once the change commits; others pick it up
     * when their copy expires. No device is notified.</p>
     *
     * @param newLimits the limits to store, each already at least one
     * @return the limits as stored
     */
    @Transactional
    public PlanLimitValues replaceFairUseLimits(final PlanLimitValues newLimits) {
        final FairUseLimitsEntity row = fairUseLimitsRepository
                .findById(FairUseLimitsEntity.SINGLE_ROW_ID)
                .orElseGet(() -> FairUseLimitsEntity.createSingleRow(newLimits));
        row.replaceLimitsWith(newLimits);
        final PlanLimitValues storedLimits = fairUseLimitsRepository.save(row).toPlanLimitValues();
        forgetCachedLimitsAfterCommit();
        LOGGER.warn("Operator set the global Fair Use limits to {}", storedLimits);
        return storedLimits;
    }

    /**
     * Returns the global Fair Use limits as stored right now, bypassing this replica's cache.
     *
     * <p>For the operator's screens and for validating a per-account raise, where a value
     * changed through another replica a moment ago must not be missed.</p>
     *
     * @return the global row's limits, or the configured ones when the row is missing
     */
    public PlanLimitValues readStoredFairUseLimits() {
        return readStoredOrConfiguredLimits();
    }

    /**
     * Reads the global row, or falls back to the configured values when there is none.
     *
     * @return the limits in force
     */
    private PlanLimitValues readStoredOrConfiguredLimits() {
        return fairUseLimitsRepository.findById(FairUseLimitsEntity.SINGLE_ROW_ID)
                .map(FairUseLimitsEntity::toPlanLimitValues)
                .orElseGet(this::configuredLimitsForMissingRow);
    }

    /**
     * Returns the configured Fair Use limits, saying that the row they stand in for is missing.
     *
     * @return {@code newtablinks.plan-limits.premium.*}
     */
    private PlanLimitValues configuredLimitsForMissingRow() {
        LOGGER.debug("No fair_use_limits row; the configured Fair Use limits hold");
        return planLimitProperties.premium();
    }

    /**
     * Drops this replica's cached copy once the surrounding transaction commits, or at once when
     * there is none.
     */
    private void forgetCachedLimitsAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cachedFairUseLimits = null;
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cachedFairUseLimits = null;
            }
        });
    }

    /**
     * The global limits as one replica last read them.
     *
     * @param limits    the limits read
     * @param freshUntil when they have to be read again
     */
    private record CachedFairUseLimits(PlanLimitValues limits, Instant freshUntil) {

        /**
         * Tells whether the copy may still be used.
         *
         * @param now the current time
         * @return {@code true} before {@link #freshUntil}
         */
        boolean isFreshAt(final Instant now) {
            return now.isBefore(freshUntil);
        }
    }
}
