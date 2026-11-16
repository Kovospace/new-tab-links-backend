package com.kovospace.newtablinks.fairuse.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.common.exceptions.FairUseOverrideBelowGlobalLimitException;
import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.models.EffectivePlanLimits;
import com.kovospace.newtablinks.common.models.PlanLimitValues;
import com.kovospace.newtablinks.common.services.PlanLimitPolicy;
import com.kovospace.newtablinks.fairuse.dtos.FairUseLimitOverridesDto;
import com.kovospace.newtablinks.fairuse.dtos.UserFairUseLimitsDto;
import com.kovospace.newtablinks.fairuse.dtos.UserFairUseLimitsSaveRequestDto;
import com.kovospace.newtablinks.fairuse.mappers.FairUseLimitsMapperImpl;
import com.kovospace.newtablinks.fairuse.models.FairUseLimitOverrides;
import com.kovospace.newtablinks.fairuse.models.UserFairUseOverrideEntity;
import com.kovospace.newtablinks.fairuse.repositories.UserFairUseOverrideRepository;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import com.kovospace.newtablinks.user.services.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the rules of the operator's per-account Fair Use raise: it only raises, all-null removes
 * it, every change tells the account's devices, and an unknown account is a 404.
 */
class UserFairUseOverrideAdministrationServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private static final PlanLimitValues GLOBAL_FAIR_USE =
            new PlanLimitValues(50, 50, 25, 25, 500, 500, 1000, 100);

    private final UserService userService = mock(UserService.class);
    private final UserFairUseOverrideRepository userFairUseOverrideRepository =
            mock(UserFairUseOverrideRepository.class);
    private final GlobalFairUseLimitsService globalFairUseLimitsService =
            mock(GlobalFairUseLimitsService.class);
    private final PlanLimitPolicy planLimitPolicy = mock(PlanLimitPolicy.class);
    private final UserDataChangePublisher userDataChangePublisher =
            mock(UserDataChangePublisher.class);

    private final UserFairUseOverrideAdministrationService administrationService =
            new UserFairUseOverrideAdministrationService(
                    userService,
                    userFairUseOverrideRepository,
                    new AccountFairUseLimitsService(
                            globalFairUseLimitsService, userFairUseOverrideRepository),
                    globalFairUseLimitsService,
                    planLimitPolicy,
                    userDataChangePublisher,
                    new FairUseLimitsMapperImpl());

    /**
     * Gives every test the same global limits and a premium account with them.
     */
    @BeforeEach
    void givenTheGlobalLimitsAndAPremiumAccount() {
        when(globalFairUseLimitsService.readStoredFairUseLimits()).thenReturn(GLOBAL_FAIR_USE);
        when(globalFairUseLimitsService.currentFairUseLimits()).thenReturn(GLOBAL_FAIR_USE);
        when(planLimitPolicy.effectiveLimitsFor(ACCOUNT_ID))
                .thenReturn(new EffectivePlanLimits(true, GLOBAL_FAIR_USE, GLOBAL_FAIR_USE));
        when(userFairUseOverrideRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("a raise below the global value is refused, naming every offending field")
    void shouldRefuseAnOverrideBelowTheGlobalValue() {
        final UserFairUseLimitsSaveRequestDto request = new UserFairUseLimitsSaveRequestDto(
                new FairUseLimitOverridesDto(49, 80, null, null, 499, null, null, null), "why");

        assertThatThrownBy(() -> administrationService.replaceFairUseOverrideOf(ACCOUNT_ID, request))
                .isInstanceOfSatisfying(FairUseOverrideBelowGlobalLimitException.class, refusal ->
                        assertThat(refusal.getValidationErrors()).containsExactly(
                                "overrides.profiles: must be at least 50, the global Fair Use limit",
                                "overrides.linksPerWorkspace: must be at least 500, the global "
                                        + "Fair Use limit"));
        verify(userFairUseOverrideRepository, never()).save(any());
        verify(userDataChangePublisher, never()).publishChangeFor(ACCOUNT_ID);
    }

    @Test
    @DisplayName("a raise at or above the global value is stored, with its note, and announced")
    void shouldStoreARaiseAndTellTheAccountsDevices() {
        final UserFairUseLimitsSaveRequestDto request = new UserFairUseLimitsSaveRequestDto(
                new FairUseLimitOverridesDto(50, null, null, null, 2000, null, null, null),
                "  Teacher  ");

        administrationService.replaceFairUseOverrideOf(ACCOUNT_ID, request);

        verify(userFairUseOverrideRepository).save(argThat(stored ->
                stored.getUserId().equals(ACCOUNT_ID)
                        && "Teacher".equals(stored.getNote())
                        && stored.toOverrides().linksPerWorkspace() == 2000));
        verify(userDataChangePublisher).publishChangeFor(ACCOUNT_ID);
    }

    @Test
    @DisplayName("all-null overrides remove an existing override and announce it")
    void shouldRemoveTheOverrideWhenNothingIsRaised() {
        final UserFairUseOverrideEntity existing = new UserFairUseOverrideEntity(ACCOUNT_ID);
        existing.replaceWith(new FairUseLimitOverrides(80, null, null, null, null, null, null, null),
                "old");
        when(userFairUseOverrideRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(existing));

        administrationService.replaceFairUseOverrideOf(ACCOUNT_ID,
                new UserFairUseLimitsSaveRequestDto(
                        new FairUseLimitOverridesDto(null, null, null, null, null, null, null, null),
                        "ignored"));

        verify(userFairUseOverrideRepository).delete(existing);
        verify(userFairUseOverrideRepository, never()).save(any());
        verify(userDataChangePublisher).publishChangeFor(ACCOUNT_ID);
    }

    @Test
    @DisplayName("an account without an override reads as all-null overrides and a null note")
    void shouldDescribeAnAccountWithoutOverride() {
        final UserFairUseLimitsDto described = administrationService.findFairUseLimitsOf(ACCOUNT_ID);

        assertThat(described.overrides())
                .isEqualTo(new FairUseLimitOverridesDto(null, null, null, null, null, null, null, null));
        assertThat(described.note()).isNull();
        assertThat(described.fairUse().profiles()).isEqualTo(50);
        assertThat(described.effective().linksPerWorkspace()).isEqualTo(500);
        assertThat(described.premium()).isTrue();
    }

    @Test
    @DisplayName("deleting where there is no override announces nothing")
    void shouldAnnounceNothingWhenThereWasNothingToDelete() {
        administrationService.deleteFairUseOverrideOf(ACCOUNT_ID);

        verify(userFairUseOverrideRepository, never()).delete(any());
        verify(userDataChangePublisher, never()).publishChangeFor(ACCOUNT_ID);
    }

    @Test
    @DisplayName("an unknown account is reported as not found")
    void shouldReportAnUnknownAccount() {
        when(userService.getRequiredUserEntity(ACCOUNT_ID))
                .thenThrow(new ResourceNotFoundException("User", ACCOUNT_ID));

        assertThatThrownBy(() -> administrationService.findFairUseLimitsOf(ACCOUNT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> administrationService.deleteFairUseOverrideOf(ACCOUNT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
