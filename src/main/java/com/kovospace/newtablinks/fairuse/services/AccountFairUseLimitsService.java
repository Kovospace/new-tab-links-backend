package com.kovospace.newtablinks.fairuse.services;

import com.kovospace.newtablinks.common.models.PlanLimitValues;
import com.kovospace.newtablinks.fairuse.models.FairUseLimitOverrides;
import com.kovospace.newtablinks.fairuse.models.UserFairUseOverrideEntity;
import com.kovospace.newtablinks.fairuse.repositories.UserFairUseOverrideRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The Fair Use limits of one account: the global ones, raised wherever the operator raised them
 * for that account.
 *
 * <p>The answer every limit check builds on - {@code PlanLimitPolicy} lays the free plan's own
 * numbers over it for an account that is not premium. Read live on every call; only the global
 * half is cached (see {@link GlobalFairUseLimitsService}).</p>
 *
 * @since 0.0.20
 */
@Service
public class AccountFairUseLimitsService {

    private final GlobalFairUseLimitsService globalFairUseLimitsService;
    private final UserFairUseOverrideRepository userFairUseOverrideRepository;

    /**
     * Creates the service.
     *
     * @param globalFairUseLimitsService    the global Fair Use limits
     * @param userFairUseOverrideRepository the per-account raises
     */
    public AccountFairUseLimitsService(
            final GlobalFairUseLimitsService globalFairUseLimitsService,
            final UserFairUseOverrideRepository userFairUseOverrideRepository) {

        this.globalFairUseLimitsService = globalFairUseLimitsService;
        this.userFairUseOverrideRepository = userFairUseOverrideRepository;
    }

    /**
     * Returns the Fair Use limits of one account.
     *
     * @param userId identifier of the account
     * @return the global Fair Use limits, each raised to the account's override where that is set
     *         and higher
     */
    public PlanLimitValues fairUseLimitsFor(final UUID userId) {
        return findOverridesOf(userId).raise(globalFairUseLimitsService.currentFairUseLimits());
    }

    /**
     * Returns the limits the operator raised for one account.
     *
     * @param userId identifier of the account
     * @return its overrides, {@link FairUseLimitOverrides#NONE} when it has none
     */
    public FairUseLimitOverrides findOverridesOf(final UUID userId) {
        return userFairUseOverrideRepository.findById(userId)
                .map(UserFairUseOverrideEntity::toOverrides)
                .orElse(FairUseLimitOverrides.NONE);
    }

    /**
     * Returns which of the given accounts have any limit raised, in one query.
     *
     * @param userIds identifiers of the accounts
     * @return those among them with an override; empty when none has, or when none was given
     */
    public Set<UUID> findUserIdsHavingOverrideAmong(final Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(userFairUseOverrideRepository.findUserIdsHavingOverrideAmong(userIds));
    }
}
