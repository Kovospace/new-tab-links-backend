package com.kovospace.newtablinks.common.services;

import com.kovospace.newtablinks.common.config.FreePlanLimitProperties;
import com.kovospace.newtablinks.common.exceptions.FreePlanLimitReachedException;
import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.models.FreePlanLimit;
import com.kovospace.newtablinks.entitlement.services.EntitlementStandingService;
import com.kovospace.newtablinks.user.repositories.UserDeviceRepository;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refuses a write that would grow an account that is not premium past a free plan limit.
 *
 * <p>Premium is the live answer - the account's entitlement judged at this moment
 * ({@link EntitlementStandingService#isAccountProNow(UUID)}), never a stored flag - so an
 * account whose subscription lapsed is held to these limits from that moment on, and a premium
 * account is held only to the Fair Use Policy caps.</p>
 *
 * <p>The profile and workspace limits are asked by {@link FairUseLimitGuard}, before its own
 * caps and after it has locked and counted, so a free account meets the lower limit first and
 * the counts are taken once. The device limit is asked directly by the device service, because
 * the Fair Use Policy has no device cap.</p>
 *
 * <p><strong>Only growth is refused</strong>, exactly as with the Fair Use Policy: data an
 * account holds above a limit - from a premium period, or from before the limit existed - is
 * never deleted and stays editable and deletable; a known installation over the limit keeps
 * signing in.</p>
 *
 * <p>Every entry point runs inside the caller's transaction ({@link Propagation#MANDATORY})
 * under the account's row lock, for the reason given on {@link FairUseLimitGuard}.</p>
 *
 * @since 0.0.17
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class FreePlanLimitGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(FreePlanLimitGuard.class);

    private static final String ACCOUNT_RESOURCE_NAME = "User";

    private final FreePlanLimitProperties freePlanLimitProperties;
    private final EntitlementStandingService entitlementStandingService;
    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;

    /**
     * Creates the guard.
     *
     * @param freePlanLimitProperties    the limits
     * @param entitlementStandingService tells whether the account is premium right now
     * @param userRepository             locks the account row
     * @param userDeviceRepository       counts synchronised installations
     */
    public FreePlanLimitGuard(
            final FreePlanLimitProperties freePlanLimitProperties,
            final EntitlementStandingService entitlementStandingService,
            final UserRepository userRepository,
            final UserDeviceRepository userDeviceRepository) {

        this.freePlanLimitProperties = freePlanLimitProperties;
        this.entitlementStandingService = entitlementStandingService;
        this.userRepository = userRepository;
        this.userDeviceRepository = userDeviceRepository;
    }

    /**
     * Refuses one more synchronised installation when a free account already has as many as its
     * plan allows.
     *
     * <p>Call it only for an installation the account does not know yet - one already recorded
     * keeps signing in however many there are.</p>
     *
     * @param ownerId identifier of the account
     * @throws FreePlanLimitReachedException when the account is not premium and already holds the
     *                                       maximum or more
     * @throws ResourceNotFoundException     when the account does not exist
     */
    public void requireRoomForAnotherSynchronisedInstallation(final UUID ownerId) {
        userRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_RESOURCE_NAME, ownerId));
        if (isHeldToFreePlanLimits(ownerId)) {
            requireRoomForOneMore(FreePlanLimit.DEVICES,
                    userDeviceRepository.countByUserIdAndInstallationIdIsNotNull(ownerId));
        }
    }

    /**
     * Tells whether an account is held to the free plan's limits, i.e. is not premium now.
     *
     * @param ownerId identifier of the account
     * @return {@code true} when no entitlement grants it premium at this moment
     */
    public boolean isHeldToFreePlanLimits(final UUID ownerId) {
        return !entitlementStandingService.isAccountProNow(ownerId);
    }

    /**
     * Refuses adding one record to a collection of the given size, for a free account.
     *
     * <p>The caller has already locked the account and decided, through
     * {@link #isHeldToFreePlanLimits(UUID)}, that the account is free.</p>
     *
     * @param limit        the limit
     * @param currentCount the collection's size now
     * @throws FreePlanLimitReachedException when one more would exceed the limit
     */
    public void requireRoomForOneMore(final FreePlanLimit limit, final long currentCount) {
        final int maximum = freePlanLimitProperties.maximumFor(limit);
        if (currentCount + 1 > maximum) {
            LOGGER.info("Refused a write past the free plan limit {} of {}", limit, maximum);
            throw new FreePlanLimitReachedException(limit, maximum);
        }
    }

    /**
     * Refuses a collection of a free account that a sync push left above its limit and larger
     * than it started.
     *
     * @param limit       the limit
     * @param countBefore the collection's size before the batch
     * @param countAfter  its size after the batch
     * @param ownerId     identifier of the account, for the log
     * @throws FreePlanLimitReachedException when the batch grew it past the limit
     */
    public void refuseGrowthPastLimit(
            final FreePlanLimit limit,
            final long countBefore,
            final long countAfter,
            final UUID ownerId) {

        final int maximum = freePlanLimitProperties.maximumFor(limit);
        if (countAfter > maximum && countAfter > countBefore) {
            LOGGER.info("Refused a sync push growing free account {} from {} to {} past the free "
                    + "plan limit {} of {}", ownerId, countBefore, countAfter, limit, maximum);
            throw new FreePlanLimitReachedException(limit, maximum);
        }
    }
}
