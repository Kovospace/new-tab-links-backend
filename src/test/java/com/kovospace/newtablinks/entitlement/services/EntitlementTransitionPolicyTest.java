package com.kovospace.newtablinks.entitlement.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.kovospace.newtablinks.entitlement.models.ChargedAmount;
import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignal;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalKind;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalOutcome;
import com.kovospace.newtablinks.entitlement.models.EntitlementSource;
import com.kovospace.newtablinks.entitlement.models.EntitlementStatus;
import com.kovospace.newtablinks.entitlement.models.PaymentProvider;
import com.kovospace.newtablinks.entitlement.models.ProviderPurchaseReferences;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the rules that decide what one payment signal does to one entitlement - above all that
 * events arriving out of order cannot roll an entitlement back, and that a failure never revokes.
 *
 * @since 0.0.9
 */
class EntitlementTransitionPolicyTest {

    private static final Instant T0 = Instant.parse("2026-09-27T10:00:00Z");
    private static final Instant FIRST_PERIOD_END = T0.plus(Duration.ofDays(365));
    private static final Instant SECOND_PERIOD_END = FIRST_PERIOD_END.plus(Duration.ofDays(365));
    private static final ProviderPurchaseReferences SUBSCRIPTION_ONE =
            new ProviderPurchaseReferences("cust_1", "sub_1", "prod_yearly", null);
    private static final ProviderPurchaseReferences SUBSCRIPTION_TWO =
            new ProviderPurchaseReferences("cust_1", "sub_2", "prod_yearly", null);
    private static final ProviderPurchaseReferences LIFETIME_ORDER =
            new ProviderPurchaseReferences("cust_1", null, "prod_lifetime", "ord_1");
    private static final ChargedAmount YEARLY_PRICE = new ChargedAmount(566, "EUR");

    private final EntitlementTransitionPolicy policy = new EntitlementTransitionPolicy();

    @Test
    @DisplayName("a paid event arriving after the past-due that followed it is refused as stale")
    void shouldRefuseAnOlderPaymentArrivingAfterANewerFailure() {
        final EntitlementEntity entitlement = subscriptionPaidUntil(FIRST_PERIOD_END, T0);
        policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_PAYMENT_FAILED, T0.plusSeconds(20), null));

        final EntitlementSignalOutcome outcome = policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_PAID, T0.plusSeconds(10), SECOND_PERIOD_END));

        assertThat(outcome).isEqualTo(EntitlementSignalOutcome.IGNORED_STALE);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.PAST_DUE);
        assertThat(entitlement.getPaidUntil()).isEqualTo(FIRST_PERIOD_END);
        assertThat(entitlement.getLastProviderEventAt()).isEqualTo(T0.plusSeconds(20));
    }

    @Test
    @DisplayName("a cancellation delivered late cannot undo the renewal that came after it")
    void shouldRefuseAnOlderCancellationArrivingAfterANewerPayment() {
        final EntitlementEntity entitlement = subscriptionPaidUntil(FIRST_PERIOD_END, T0);
        policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_PAID, T0.plusSeconds(60), SECOND_PERIOD_END));

        final EntitlementSignalOutcome outcome = policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_CANCELED, T0.plusSeconds(30), null));

        assertThat(outcome).isEqualTo(EntitlementSignalOutcome.IGNORED_STALE);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(entitlement.getPaidUntil()).isEqualTo(SECOND_PERIOD_END);
    }

    @Test
    @DisplayName("two events stamped with the same millisecond are both applied")
    void shouldApplyEventsFromTheSameMillisecond() {
        final EntitlementEntity entitlement = subscriptionPaidUntil(FIRST_PERIOD_END, T0);

        final EntitlementSignalOutcome outcome = policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_ACTIVATED, T0, FIRST_PERIOD_END));

        assertThat(outcome).isEqualTo(EntitlementSignalOutcome.APPLIED);
    }

    @Test
    @DisplayName("past due marks the entitlement and leaves the paid period, and pro, in place")
    void shouldMarkPastDueWithoutRevoking() {
        final EntitlementEntity entitlement = subscriptionPaidUntil(FIRST_PERIOD_END, T0);

        policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_PAYMENT_FAILED, T0.plusSeconds(1),
                SECOND_PERIOD_END));

        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.PAST_DUE);
        assertThat(entitlement.getPaidUntil()).isEqualTo(FIRST_PERIOD_END);
        assertThat(entitlement.grantsProAt(FIRST_PERIOD_END.minusSeconds(1))).isTrue();
        assertThat(entitlement.grantsProAt(FIRST_PERIOD_END.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("a scheduled cancellation keeps pro until the day it names")
    void shouldKeepProUntilAScheduledCancellationTakesEffect() {
        final EntitlementEntity entitlement = subscriptionPaidUntil(FIRST_PERIOD_END, T0);

        policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_CANCELLATION_SCHEDULED, T0.plusSeconds(1),
                FIRST_PERIOD_END));

        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.SCHEDULED_CANCEL);
        assertThat(entitlement.grantsProAt(FIRST_PERIOD_END.minusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("a renewal moves the paid period forward and records what was charged")
    void shouldExtendThePaidPeriodOnRenewal() {
        final EntitlementEntity entitlement = subscriptionPaidUntil(FIRST_PERIOD_END, T0);

        policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_PAID, T0.plusSeconds(1), SECOND_PERIOD_END));

        assertThat(entitlement.getPaidUntil()).isEqualTo(SECOND_PERIOD_END);
        assertThat(entitlement.getChargedAmountInMinorUnits()).isEqualTo(566L);
        assertThat(entitlement.getChargedCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("a standing lifetime purchase ignores every subscription signal")
    void shouldLetALifetimePurchaseOutrankSubscriptions() {
        final EntitlementEntity entitlement = newEntitlement();
        policy.apply(entitlement, true, lifetimeSignal(T0));

        final EntitlementSignalOutcome outcome = policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_EXPIRED, T0.plusSeconds(5), null));

        assertThat(outcome).isEqualTo(EntitlementSignalOutcome.IGNORED_UNRELATED);
        assertThat(entitlement.getSource()).isEqualTo(EntitlementSource.LIFETIME);
        assertThat(entitlement.grantsProAt(T0.plus(Duration.ofDays(10_000)))).isTrue();
    }

    @Test
    @DisplayName("buying lifetime on top of a subscription replaces it")
    void shouldReplaceASubscriptionWithALifetimePurchase() {
        final EntitlementEntity entitlement = subscriptionPaidUntil(FIRST_PERIOD_END, T0);

        policy.apply(entitlement, false, lifetimeSignal(T0.plusSeconds(1)));

        assertThat(entitlement.getSource()).isEqualTo(EntitlementSource.LIFETIME);
        assertThat(entitlement.getPaidUntil()).isNull();
        assertThat(entitlement.getProviderSubscriptionId()).isNull();
        assertThat(entitlement.getProviderOrderId()).isEqualTo("ord_1");
    }

    @Test
    @DisplayName("buying lifetime on top of a live subscription leaves that subscription pending "
            + "cancellation")
    void shouldCaptureTheSupersededSubscriptionForCancellation() {
        final EntitlementEntity entitlement = subscriptionPaidUntil(FIRST_PERIOD_END, T0);

        policy.apply(entitlement, false, lifetimeSignal(T0.plusSeconds(1)));

        assertThat(entitlement.getSupersededSubscriptionId()).isEqualTo("sub_1");
        assertThat(entitlement.getSupersededSubscriptionCancelledAt()).isNull();
        assertThat(entitlement.pendingSupersededSubscriptionId()).contains("sub_1");
    }

    @Test
    @DisplayName("a subscription already scheduled to cancel is still cancelled outright")
    void shouldCaptureASubscriptionAlreadyScheduledToCancel() {
        final EntitlementEntity entitlement = subscriptionPaidUntil(FIRST_PERIOD_END, T0);
        policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_CANCELLATION_SCHEDULED, T0.plusSeconds(1),
                FIRST_PERIOD_END));

        policy.apply(entitlement, false, lifetimeSignal(T0.plusSeconds(2)));

        assertThat(entitlement.pendingSupersededSubscriptionId()).contains("sub_1");
    }

    @Test
    @DisplayName("a subscription already cancelled outright is not scheduled for cancellation")
    void shouldNotCaptureAnAlreadyCancelledSubscription() {
        final EntitlementEntity entitlement = subscriptionPaidUntil(FIRST_PERIOD_END, T0);
        policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_CANCELED, T0.plusSeconds(1), null));

        policy.apply(entitlement, false, lifetimeSignal(T0.plusSeconds(2)));

        assertThat(entitlement.getSource()).isEqualTo(EntitlementSource.LIFETIME);
        assertThat(entitlement.pendingSupersededSubscriptionId()).isEmpty();
    }

    @Test
    @DisplayName("a lifetime purchase on a fresh account has no subscription to cancel")
    void shouldNotCaptureAnythingForAFirstLifetimePurchase() {
        final EntitlementEntity entitlement = newEntitlement();

        policy.apply(entitlement, true, lifetimeSignal(T0));

        assertThat(entitlement.getSupersededSubscriptionId()).isNull();
    }

    @Test
    @DisplayName("the canceled and expired events the cancellation triggers leave lifetime intact")
    void shouldIgnoreTheSupersededSubscriptionsLateCancellation() {
        final EntitlementEntity entitlement = subscriptionPaidUntil(FIRST_PERIOD_END, T0);
        policy.apply(entitlement, false, lifetimeSignal(T0.plusSeconds(1)));

        final EntitlementSignalOutcome cancelled = policy.apply(entitlement, false,
                subscriptionSignal(EntitlementSignalKind.SUBSCRIPTION_CANCELED,
                        T0.plusSeconds(2), null));
        final EntitlementSignalOutcome expired = policy.apply(entitlement, false,
                subscriptionSignal(EntitlementSignalKind.SUBSCRIPTION_EXPIRED,
                        T0.plusSeconds(3), null));

        assertThat(cancelled).isEqualTo(EntitlementSignalOutcome.IGNORED_UNRELATED);
        assertThat(expired).isEqualTo(EntitlementSignalOutcome.IGNORED_UNRELATED);
        assertThat(entitlement.getSource()).isEqualTo(EntitlementSource.LIFETIME);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(entitlement.getProviderSubscriptionId()).isNull();
        assertThat(entitlement.grantsProAt(T0.plus(Duration.ofDays(10_000)))).isTrue();
        assertThat(entitlement.pendingSupersededSubscriptionId()).contains("sub_1");
    }

    @Test
    @DisplayName("a lifetime purchase older than newer subscription events is still applied, "
            + "captures the subscription, and does not move the newest-event time back")
    void shouldApplyALateLifetimePurchaseOverNewerSubscriptionEvents() {
        final EntitlementEntity entitlement = subscriptionPaidUntil(FIRST_PERIOD_END, T0);
        policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_PAID, T0.plusSeconds(100), SECOND_PERIOD_END));

        final EntitlementSignalOutcome outcome =
                policy.apply(entitlement, false, lifetimeSignal(T0.plusSeconds(50)));

        assertThat(outcome).isEqualTo(EntitlementSignalOutcome.APPLIED);
        assertThat(entitlement.getSource()).isEqualTo(EntitlementSource.LIFETIME);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(entitlement.getProviderOrderId()).isEqualTo("ord_1");
        assertThat(entitlement.pendingSupersededSubscriptionId()).contains("sub_1");
        assertThat(entitlement.getLastProviderEventAt()).isEqualTo(T0.plusSeconds(100));
    }

    @Test
    @DisplayName("a lifetime purchase delivered after its own refund does not reactivate it, "
            + "whatever its timestamp")
    void shouldLetARefundWinOverItsOwnLatePurchase() {
        final EntitlementEntity entitlement = newEntitlement();
        policy.apply(entitlement, true, lifetimeSignal(T0));
        policy.apply(entitlement, false, refundOf(LIFETIME_ORDER, T0.plusSeconds(100)));

        final EntitlementSignalOutcome older =
                policy.apply(entitlement, false, lifetimeSignal(T0.plusSeconds(10)));
        final EntitlementSignalOutcome newer =
                policy.apply(entitlement, false, lifetimeSignal(T0.plusSeconds(200)));

        assertThat(older).isEqualTo(EntitlementSignalOutcome.IGNORED_STALE);
        assertThat(newer).isEqualTo(EntitlementSignalOutcome.IGNORED_STALE);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.REFUNDED);
        assertThat(entitlement.grantsProAt(T0.plusSeconds(300))).isFalse();
        assertThat(entitlement.getLastProviderEventAt()).isEqualTo(T0.plusSeconds(100));
    }

    @Test
    @DisplayName("an older copy of the lifetime purchase already applied is refused as stale")
    void shouldRefuseAnOlderCopyOfTheSameLifetimePurchase() {
        final EntitlementEntity entitlement = newEntitlement();
        policy.apply(entitlement, true, lifetimeSignal(T0.plusSeconds(100)));

        assertThat(policy.apply(entitlement, false, lifetimeSignal(T0)))
                .isEqualTo(EntitlementSignalOutcome.IGNORED_STALE);
        assertThat(entitlement.getLastProviderEventAt()).isEqualTo(T0.plusSeconds(100));
    }

    @Test
    @DisplayName("a status change for a subscription the entitlement does not rest on is ignored")
    void shouldIgnoreAnotherSubscriptionsFailure() {
        final EntitlementEntity entitlement = subscriptionPaidUntil(FIRST_PERIOD_END, T0);

        final EntitlementSignalOutcome outcome = policy.apply(entitlement, false, new EntitlementSignal(
                EntitlementSignalKind.SUBSCRIPTION_CANCELED, PaymentProvider.CREEM,
                T0.plusSeconds(1), null, SUBSCRIPTION_TWO, null, null));

        assertThat(outcome).isEqualTo(EntitlementSignalOutcome.IGNORED_UNRELATED);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
    }

    @Test
    @DisplayName("a refund of the lifetime order revokes it immediately; a refund of anything "
            + "else does not")
    void shouldRevokeOnlyOnARefundOfTheHeldPurchase() {
        final EntitlementEntity entitlement = newEntitlement();
        policy.apply(entitlement, true, lifetimeSignal(T0));

        final EntitlementSignalOutcome unrelated = policy.apply(entitlement, false, refundOf(
                new ProviderPurchaseReferences("cust_1", null, null, "ord_other"),
                T0.plusSeconds(1)));
        final EntitlementSignalOutcome related =
                policy.apply(entitlement, false, refundOf(LIFETIME_ORDER, T0.plusSeconds(2)));

        assertThat(unrelated).isEqualTo(EntitlementSignalOutcome.IGNORED_UNRELATED);
        assertThat(related).isEqualTo(EntitlementSignalOutcome.APPLIED);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.REFUNDED);
        assertThat(entitlement.grantsProAt(T0.plusSeconds(3))).isFalse();
    }

    @Test
    @DisplayName("an ended subscription grants nothing, even before its paid period would have run out")
    void shouldEndAccessWhenTheSubscriptionIsCanceled() {
        final EntitlementEntity entitlement = newEntitlement();
        policy.apply(entitlement, true, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_PAID, T0, FIRST_PERIOD_END));

        policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_CANCELED, T0.plusSeconds(60), FIRST_PERIOD_END));

        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.CANCELED);
        assertThat(entitlement.grantsProAt(T0.plusSeconds(120))).isFalse();
    }

    @Test
    @DisplayName("a scheduled cancellation keeps access until the paid period ends, and not after")
    void shouldKeepAccessUntilAScheduledCancellationRunsOut() {
        final EntitlementEntity entitlement = newEntitlement();
        policy.apply(entitlement, true, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_PAID, T0, FIRST_PERIOD_END));

        policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_CANCELLATION_SCHEDULED, T0.plusSeconds(60),
                FIRST_PERIOD_END));

        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.SCHEDULED_CANCEL);
        assertThat(entitlement.grantsProAt(FIRST_PERIOD_END.minusSeconds(1))).isTrue();
        assertThat(entitlement.grantsProAt(FIRST_PERIOD_END.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("a failure arriving first starts the row, so the older purchase is then stale")
    void shouldStartFromAFailureThatArrivedBeforeItsPurchase() {
        final EntitlementEntity entitlement = newEntitlement();
        policy.apply(entitlement, true, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_CANCELED, T0.plusSeconds(30), FIRST_PERIOD_END));

        final EntitlementSignalOutcome outcome = policy.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_PURCHASED, T0, FIRST_PERIOD_END));

        assertThat(outcome).isEqualTo(EntitlementSignalOutcome.IGNORED_STALE);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.CANCELED);
    }

    @Test
    @DisplayName("a refund cannot start an entitlement")
    void shouldNotStartAnEntitlementFromARefund() {
        assertThat(policy.mayStartEntitlement(refundOf(LIFETIME_ORDER, T0))).isFalse();
        assertThat(policy.mayStartEntitlement(lifetimeSignal(T0))).isTrue();
    }

    @Test
    @DisplayName("a subscription whose period end is not known yet grants nothing")
    void shouldNotGrantAnOpenEndedSubscription() {
        final EntitlementEntity entitlement = newEntitlement();

        policy.apply(entitlement, true, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_PURCHASED, T0, null));

        assertThat(entitlement.grantsProAt(T0)).isFalse();
    }

    /**
     * An entitlement resting on subscription one, paid until the given instant.
     *
     * @param paidUntil  end of the paid period
     * @param occurredAt when the purchase event happened
     * @return the entitlement
     */
    private EntitlementEntity subscriptionPaidUntil(final Instant paidUntil, final Instant occurredAt) {
        final EntitlementEntity entitlement = newEntitlement();
        policy.apply(entitlement, true, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_PURCHASED, occurredAt, paidUntil));
        return entitlement;
    }

    /**
     * A signal about subscription one.
     *
     * @param kind       what happened
     * @param occurredAt when
     * @param paidUntil  the reported period end, or {@code null}
     * @return the signal
     */
    private static EntitlementSignal subscriptionSignal(
            final EntitlementSignalKind kind, final Instant occurredAt, final Instant paidUntil) {

        return new EntitlementSignal(kind, PaymentProvider.CREEM, occurredAt, null,
                SUBSCRIPTION_ONE, paidUntil, YEARLY_PRICE);
    }

    /**
     * A lifetime purchase of order one.
     *
     * @param occurredAt when
     * @return the signal
     */
    private static EntitlementSignal lifetimeSignal(final Instant occurredAt) {
        return new EntitlementSignal(EntitlementSignalKind.LIFETIME_PURCHASED,
                PaymentProvider.CREEM, occurredAt, null, LIFETIME_ORDER, null,
                new ChargedAmount(1814, "EUR"));
    }

    /**
     * A full refund of a purchase.
     *
     * @param references the refunded purchase
     * @param occurredAt when
     * @return the signal
     */
    private static EntitlementSignal refundOf(
            final ProviderPurchaseReferences references, final Instant occurredAt) {

        return new EntitlementSignal(EntitlementSignalKind.PAYMENT_REFUNDED,
                PaymentProvider.CREEM, occurredAt, null, references, null, null);
    }

    /**
     * A brand-new entitlement for a throwaway account.
     *
     * @return the entitlement
     */
    private static EntitlementEntity newEntitlement() {
        return new EntitlementEntity(new UserEntity(
                "buyer", "buyer@example.com", null, "Buyer", UserAccountStatus.ACTIVE));
    }
}
