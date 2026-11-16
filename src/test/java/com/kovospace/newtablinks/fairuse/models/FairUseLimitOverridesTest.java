package com.kovospace.newtablinks.fairuse.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.kovospace.newtablinks.common.models.PlanLimitValues;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins that an override only ever raises: the higher of it and the global value holds, and an
 * unset limit keeps the global value.
 */
class FairUseLimitOverridesTest {

    private static final PlanLimitValues GLOBAL_LIMITS =
            new PlanLimitValues(50, 50, 25, 25, 500, 500, 1000, 100);

    @Test
    @DisplayName("a raised limit replaces the global one; an unset one keeps it")
    void shouldRaiseOnlyTheLimitsThatAreSet() {
        final FairUseLimitOverrides overrides =
                new FairUseLimitOverrides(80, null, null, null, 2000, null, null, null);

        assertThat(overrides.raise(GLOBAL_LIMITS))
                .isEqualTo(new PlanLimitValues(80, 50, 25, 25, 2000, 500, 1000, 100));
    }

    @Test
    @DisplayName("a global value that rose past an override wins - an override never lowers")
    void shouldKeepTheGlobalValueWhenItIsHigherThanTheOverride() {
        final FairUseLimitOverrides overrides =
                new FairUseLimitOverrides(60, null, null, null, null, null, null, 10);
        final PlanLimitValues raisedGlobal = new PlanLimitValues(70, 50, 25, 25, 500, 500, 1000, 100);

        assertThat(overrides.raise(raisedGlobal).profiles()).isEqualTo(70);
        assertThat(overrides.raise(raisedGlobal).devices()).isEqualTo(100);
    }

    @Test
    @DisplayName("no override is empty and leaves the global limits as they are")
    void shouldTreatNoOverrideAsEmpty() {
        assertThat(FairUseLimitOverrides.NONE.isEmpty()).isTrue();
        assertThat(FairUseLimitOverrides.NONE.raise(GLOBAL_LIMITS)).isEqualTo(GLOBAL_LIMITS);
        assertThat(new FairUseLimitOverrides(null, null, null, null, null, null, null, 1).isEmpty())
                .isFalse();
    }
}
