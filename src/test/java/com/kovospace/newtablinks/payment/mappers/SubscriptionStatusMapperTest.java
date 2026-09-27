package com.kovospace.newtablinks.payment.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.EntitlementSource;
import com.kovospace.newtablinks.entitlement.models.EntitlementStatus;
import com.kovospace.newtablinks.payment.dtos.SubscriptionStatusDto;
import com.kovospace.newtablinks.payment.models.SubscriptionPlan;
import com.kovospace.newtablinks.payment.models.SubscriptionState;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pins how an entitlement row becomes the plan and state the website binds.
 *
 * @since 0.0.9
 */
class SubscriptionStatusMapperTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-27T10:00:00Z");
    private static final Instant PAID_UNTIL = Instant.parse("2027-09-27T10:00:00Z");

    private final SubscriptionStatusMapper subscriptionStatusMapper = new SubscriptionStatusMapper();

    @Test
    @DisplayName("an account without an entitlement has no plan, state NONE, no dates and no flags")
    void shouldDescribeAccountWithoutEntitlementAsNone() {
        final SubscriptionStatusDto status =
                subscriptionStatusMapper.toDtoForAccountWithoutEntitlement();

        assertThat(status).isEqualTo(new SubscriptionStatusDto(
                null, SubscriptionState.NONE, null, null, null, null, false, false, null, null));
    }

    @ParameterizedTest(name = "{0} {1} -> plan {2}, state {3}, renews {4}")
    @CsvSource(nullValues = "null", value = {
            "SUBSCRIPTION, ACTIVE,           YEARLY_RECURRING, ACTIVE,    true",
            "SUBSCRIPTION, PAST_DUE,         YEARLY_RECURRING, PAST_DUE,  false",
            "SUBSCRIPTION, SCHEDULED_CANCEL, YEARLY_RECURRING, CANCELLED, false",
            "SUBSCRIPTION, CANCELED,         YEARLY_RECURRING, EXPIRED,   false",
            "SUBSCRIPTION, EXPIRED,          YEARLY_RECURRING, EXPIRED,   false",
            "SUBSCRIPTION, REFUNDED,         YEARLY_RECURRING, REFUNDED,  false",
            "LIFETIME,     ACTIVE,           LIFETIME,         ACTIVE,    false",
            "LIFETIME,     PAST_DUE,         LIFETIME,         PAST_DUE,  false",
            "LIFETIME,     SCHEDULED_CANCEL, LIFETIME,         CANCELLED, false",
            "LIFETIME,     CANCELED,         LIFETIME,         EXPIRED,   false",
            "LIFETIME,     EXPIRED,          LIFETIME,         EXPIRED,   false",
            "LIFETIME,     REFUNDED,         LIFETIME,         REFUNDED,  false",
            "GRANT,        ACTIVE,           null,             ACTIVE,    false",
            "GRANT,        PAST_DUE,         null,             PAST_DUE,  false",
            "GRANT,        SCHEDULED_CANCEL, null,             CANCELLED, false",
            "GRANT,        CANCELED,         null,             EXPIRED,   false",
            "GRANT,        EXPIRED,          null,             EXPIRED,   false",
            "GRANT,        REFUNDED,         null,             REFUNDED,  false"})
    @DisplayName("maps every source and status to the website's plan and state")
    void shouldMapEverySourceAndStatus(
            final EntitlementSource source,
            final EntitlementStatus status,
            final SubscriptionPlan expectedPlan,
            final SubscriptionState expectedState,
            final boolean expectedToRenew) {

        final Instant paidUntil = source == EntitlementSource.LIFETIME ? null : PAID_UNTIL;

        final SubscriptionStatusDto mapped =
                subscriptionStatusMapper.toDto(entitlement(source, status, paidUntil));

        assertThat(mapped.plan()).isEqualTo(expectedPlan);
        assertThat(mapped.state()).isEqualTo(expectedState);
        assertThat(mapped.startedAt()).isEqualTo(CREATED_AT);
        assertThat(mapped.validUntil()).isEqualTo(paidUntil);
        assertThat(mapped.renewsAt()).isEqualTo(expectedToRenew ? PAID_UNTIL : null);
        assertThat(mapped.cancelledAt()).isNull();
        assertThat(mapped.cancellable()).isFalse();
        assertThat(mapped.refundable()).isFalse();
        assertThat(mapped.refundableUntil()).isNull();
        assertThat(mapped.pendingCheckout()).isNull();
    }

    /**
     * Builds an entitlement row in the given shape, as it would have been loaded.
     *
     * <p>The source is set reflectively because nothing writes a grant yet.</p>
     *
     * @param source    where it came from
     * @param status    where it stands
     * @param paidUntil end of the paid period, or {@code null}
     * @return the entitlement
     */
    private static EntitlementEntity entitlement(
            final EntitlementSource source,
            final EntitlementStatus status,
            final Instant paidUntil) {

        final EntitlementEntity entitlement = new EntitlementEntity(new UserEntity(
                "buyer", "buyer@example.com", null, "Buyer", UserAccountStatus.ACTIVE));
        ReflectionTestUtils.setField(entitlement, "source", source);
        ReflectionTestUtils.setField(entitlement, "createdAt", CREATED_AT);
        entitlement.changeStatus(status);
        entitlement.replacePaidUntilWhenReported(paidUntil);
        return entitlement;
    }
}
