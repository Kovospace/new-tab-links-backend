package com.kovospace.newtablinks.sync.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.common.models.EffectivePlanLimits;
import com.kovospace.newtablinks.common.models.PlanLimitValues;
import com.kovospace.newtablinks.common.models.PushLimitBaseline;
import com.kovospace.newtablinks.common.services.SyncPushLimitGuard;
import java.util.Map;

/**
 * A mocked {@link SyncPushLimitGuard} that refuses nothing, for tests whose subject is not the
 * plan limits but that need a {@link SyncPushService} to run a push end to end.
 */
final class PermissiveSyncPushLimitGuard {

    /** Limits far above anything a unit test pushes. */
    private static final PlanLimitValues GENEROUS_LIMITS =
            new PlanLimitValues(1_000, 1_000, 1_000, 1_000, 1_000, 1_000, 1_000);

    /**
     * Not instantiable; a factory only.
     */
    private PermissiveSyncPushLimitGuard() {
    }

    /**
     * Creates the mock, answering every baseline request with generous limits and empty counts.
     *
     * @return the mock
     */
    static SyncPushLimitGuard create() {
        final SyncPushLimitGuard guard = mock(SyncPushLimitGuard.class);
        when(guard.captureBaselineBeforeBatch(any())).thenReturn(new PushLimitBaseline(
                new EffectivePlanLimits(true, GENEROUS_LIMITS), Map.of(), Map.of(), Map.of(), 0));
        return guard;
    }
}
