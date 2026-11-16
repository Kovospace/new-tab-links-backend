package com.kovospace.newtablinks.fairuse.services;

import com.kovospace.newtablinks.common.exceptions.FairUseOverrideBelowGlobalLimitException;
import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.models.EffectivePlanLimits;
import com.kovospace.newtablinks.common.models.PlanLimit;
import com.kovospace.newtablinks.common.models.PlanLimitValues;
import com.kovospace.newtablinks.common.services.PlanLimitPolicy;
import com.kovospace.newtablinks.fairuse.dtos.FairUseLimitOverridesDto;
import com.kovospace.newtablinks.fairuse.dtos.UserFairUseLimitsDto;
import com.kovospace.newtablinks.fairuse.dtos.UserFairUseLimitsSaveRequestDto;
import com.kovospace.newtablinks.fairuse.mappers.FairUseLimitsMapper;
import com.kovospace.newtablinks.fairuse.models.FairUseLimitOverrides;
import com.kovospace.newtablinks.fairuse.models.UserFairUseOverrideEntity;
import com.kovospace.newtablinks.fairuse.repositories.UserFairUseOverrideRepository;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import com.kovospace.newtablinks.user.services.UserService;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the operator can do to one account's Fair Use limits: read them, raise them, and take the
 * raise back.
 *
 * <p>An override only raises: every limit it sets must be at least the global Fair Use value at
 * the moment it is saved. A change is announced to the account's devices
 * ({@link UserDataChangePublisher#publishChangeFor(UUID)}, delivered after commit), so they pull
 * their new {@code planLimits} at once instead of at their next synchronisation.</p>
 *
 * <p>Like the rest of the operator's account repair, nothing here is ownership-scoped, an unknown
 * account is a plain 404, and every change writes a log line naming the account.</p>
 *
 * @since 0.0.20
 */
@Service
public class UserFairUseOverrideAdministrationService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(UserFairUseOverrideAdministrationService.class);

    /** Prefix of a rejected limit's field name in the request body. */
    private static final String OVERRIDES_FIELD_PREFIX = "overrides.";

    private final UserService userService;
    private final UserFairUseOverrideRepository userFairUseOverrideRepository;
    private final AccountFairUseLimitsService accountFairUseLimitsService;
    private final GlobalFairUseLimitsService globalFairUseLimitsService;
    private final PlanLimitPolicy planLimitPolicy;
    private final UserDataChangePublisher userDataChangePublisher;
    private final FairUseLimitsMapper fairUseLimitsMapper;

    /**
     * Creates the service.
     *
     * @param userService                   resolves the account, or reports it missing
     * @param userFairUseOverrideRepository persistence for the per-account raises
     * @param accountFairUseLimitsService   reads an account's raises
     * @param globalFairUseLimitsService    the global Fair Use limits
     * @param planLimitPolicy               the limits an account gets now
     * @param userDataChangePublisher       tells the account's devices to pull
     * @param fairUseLimitsMapper           converts limits for the client
     */
    public UserFairUseOverrideAdministrationService(
            final UserService userService,
            final UserFairUseOverrideRepository userFairUseOverrideRepository,
            final AccountFairUseLimitsService accountFairUseLimitsService,
            final GlobalFairUseLimitsService globalFairUseLimitsService,
            final PlanLimitPolicy planLimitPolicy,
            final UserDataChangePublisher userDataChangePublisher,
            final FairUseLimitsMapper fairUseLimitsMapper) {

        this.userService = userService;
        this.userFairUseOverrideRepository = userFairUseOverrideRepository;
        this.accountFairUseLimitsService = accountFairUseLimitsService;
        this.globalFairUseLimitsService = globalFairUseLimitsService;
        this.planLimitPolicy = planLimitPolicy;
        this.userDataChangePublisher = userDataChangePublisher;
        this.fairUseLimitsMapper = fairUseLimitsMapper;
    }

    /**
     * Reads one account's Fair Use overrides, the global limits and what the account gets now.
     *
     * @param userId identifier of the account
     * @return the account's Fair Use limits; all-null overrides and a null note when it has none
     * @throws ResourceNotFoundException when no account has that identifier
     */
    @Transactional(readOnly = true)
    public UserFairUseLimitsDto findFairUseLimitsOf(final UUID userId) {
        userService.getRequiredUserEntity(userId);
        return describeFairUseLimitsOf(userId);
    }

    /**
     * Replaces the Fair Use limits raised for one account; with nothing raised, removes them.
     *
     * @param userId      identifier of the account
     * @param saveRequest the raised limits and the note
     * @return the account's Fair Use limits after the change
     * @throws ResourceNotFoundException                when no account has that identifier
     * @throws FairUseOverrideBelowGlobalLimitException when a raised limit is below the global one
     */
    @Transactional
    public UserFairUseLimitsDto replaceFairUseOverrideOf(
            final UUID userId,
            final UserFairUseLimitsSaveRequestDto saveRequest) {

        userService.getRequiredUserEntity(userId);
        final FairUseLimitOverrides overrides = toOverrides(saveRequest.overrides());
        if (overrides.isEmpty()) {
            removeOverrideIfPresent(userId);
            return describeFairUseLimitsOf(userId);
        }
        requireOnlyRaises(overrides, globalFairUseLimitsService.readStoredFairUseLimits());

        final UserFairUseOverrideEntity override = userFairUseOverrideRepository.findById(userId)
                .orElseGet(() -> new UserFairUseOverrideEntity(userId));
        override.replaceWith(overrides, blankToNull(saveRequest.note()));
        userFairUseOverrideRepository.save(override);
        userDataChangePublisher.publishChangeFor(userId);
        LOGGER.warn("Operator raised the Fair Use limits of account {} to {}", userId, overrides);
        return describeFairUseLimitsOf(userId);
    }

    /**
     * Removes the Fair Use limits raised for one account; the global ones hold for it again.
     *
     * <p>Idempotent: an account with no override is left as it is.</p>
     *
     * @param userId identifier of the account
     * @throws ResourceNotFoundException when no account has that identifier
     */
    @Transactional
    public void deleteFairUseOverrideOf(final UUID userId) {
        userService.getRequiredUserEntity(userId);
        removeOverrideIfPresent(userId);
    }

    /**
     * Deletes an account's override when it has one, and tells its devices.
     *
     * @param userId identifier of the account, known to exist
     */
    private void removeOverrideIfPresent(final UUID userId) {
        userFairUseOverrideRepository.findById(userId).ifPresent(override -> {
            userFairUseOverrideRepository.delete(override);
            userDataChangePublisher.publishChangeFor(userId);
            LOGGER.warn("Operator removed the Fair Use override of account {}", userId);
        });
    }

    /**
     * Builds the operator's view of one account's Fair Use limits.
     *
     * @param userId identifier of the account, known to exist
     * @return its overrides, note, the global limits and its effective limits
     */
    private UserFairUseLimitsDto describeFairUseLimitsOf(final UUID userId) {
        final EffectivePlanLimits effectiveLimits = planLimitPolicy.effectiveLimitsFor(userId);
        final String note = userFairUseOverrideRepository.findById(userId)
                .map(UserFairUseOverrideEntity::getNote)
                .orElse(null);
        return new UserFairUseLimitsDto(
                fairUseLimitsMapper.toDto(accountFairUseLimitsService.findOverridesOf(userId)),
                note,
                fairUseLimitsMapper.toDto(globalFairUseLimitsService.readStoredFairUseLimits()),
                fairUseLimitsMapper.toDto(effectiveLimits.values()),
                effectiveLimits.premium());
    }

    /**
     * Refuses any raised limit below the global value of that limit.
     *
     * @param overrides    the raised limits
     * @param globalLimits the global Fair Use limits
     * @throws FairUseOverrideBelowGlobalLimitException naming every offending limit
     */
    private static void requireOnlyRaises(
            final FairUseLimitOverrides overrides,
            final PlanLimitValues globalLimits) {

        final List<String> validationErrors = Arrays.stream(PlanLimit.values())
                .filter(limit -> isBelowGlobal(overrides.overrideFor(limit), globalLimits, limit))
                .map(limit -> "%s%s: must be at least %d, the global Fair Use limit".formatted(
                        OVERRIDES_FIELD_PREFIX, jsonFieldNameOf(limit),
                        globalLimits.maximumFor(limit)))
                .toList();
        if (!validationErrors.isEmpty()) {
            throw new FairUseOverrideBelowGlobalLimitException(validationErrors);
        }
    }

    /**
     * Tells whether a raised limit is below its global value.
     *
     * @param override     the raised value, or {@code null}
     * @param globalLimits the global Fair Use limits
     * @param limit        which limit it is
     * @return {@code true} when it is set and lower than the global value
     */
    private static boolean isBelowGlobal(
            final Integer override,
            final PlanLimitValues globalLimits,
            final PlanLimit limit) {

        return override != null && override < globalLimits.maximumFor(limit);
    }

    /**
     * Returns the name a limit has in the request body - {@code WORKSPACES_PER_PROFILE} is
     * {@code workspacesPerProfile}.
     *
     * @param limit the limit
     * @return its field name
     */
    private static String jsonFieldNameOf(final PlanLimit limit) {
        final String[] words = limit.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder fieldName = new StringBuilder(words[0]);
        for (int index = 1; index < words.length; index++) {
            fieldName.append(Character.toUpperCase(words[index].charAt(0)))
                    .append(words[index].substring(1));
        }
        return fieldName.toString();
    }

    /**
     * Turns the request's overrides into a value; absent is read as none raised.
     *
     * @param overridesDto the request's overrides, or {@code null}
     * @return the overrides
     */
    private static FairUseLimitOverrides toOverrides(final FairUseLimitOverridesDto overridesDto) {
        if (overridesDto == null) {
            return FairUseLimitOverrides.NONE;
        }
        return new FairUseLimitOverrides(
                overridesDto.profiles(),
                overridesDto.workspacesPerProfile(),
                overridesDto.groupsPerWorkspace(),
                overridesDto.subgroupsPerGroup(),
                overridesDto.linksPerWorkspace(),
                overridesDto.closedTabs(),
                overridesDto.browsingHistory(),
                overridesDto.devices());
    }

    /**
     * Stores a blank note as no note.
     *
     * @param note the note as sent
     * @return the trimmed note, or {@code null} when it is absent or blank
     */
    private static String blankToNull(final String note) {
        return note == null || note.isBlank() ? null : note.strip();
    }
}
