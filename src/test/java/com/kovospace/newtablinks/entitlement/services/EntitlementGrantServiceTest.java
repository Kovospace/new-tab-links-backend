package com.kovospace.newtablinks.entitlement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.common.exceptions.PaidEntitlementRevocationException;
import com.kovospace.newtablinks.entitlement.models.ChargedAmount;
import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignal;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalKind;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalOutcome;
import com.kovospace.newtablinks.entitlement.models.EntitlementSource;
import com.kovospace.newtablinks.entitlement.models.EntitlementStatus;
import com.kovospace.newtablinks.entitlement.models.OperatorProDecisionOutcome;
import com.kovospace.newtablinks.entitlement.models.PaymentProvider;
import com.kovospace.newtablinks.entitlement.models.PremiumGrantTerm;
import com.kovospace.newtablinks.entitlement.models.ProviderPurchaseReferences;
import com.kovospace.newtablinks.entitlement.repositories.EntitlementRepository;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

/**
 * Pins what the operator's premium checkbox does to an entitlement: a grant is written only where
 * nothing grants or over an earlier grant when a term is named, only a grant can be taken back,
 * and a later payment still takes a grant over.
 *
 * <p>The service judges against the real clock, so every fixture here is placed relative to
 * now: far enough in the past or future that the test cannot straddle a boundary.</p>
 *
 * @since 0.0.10
 */
class EntitlementGrantServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final ProviderPurchaseReferences OLD_SUBSCRIPTION =
            new ProviderPurchaseReferences("cust_1", "sub_old", "prod_yearly", "ord_sub");
    private static final ProviderPurchaseReferences NEW_SUBSCRIPTION =
            new ProviderPurchaseReferences("cust_1", "sub_new", "prod_yearly", null);
    private static final ProviderPurchaseReferences LIFETIME_ORDER =
            new ProviderPurchaseReferences("cust_1", null, "prod_lifetime", "ord_life");
    private static final ChargedAmount YEARLY_PRICE = new ChargedAmount(566, "EUR");
    private static final EntitlementTransitionPolicy POLICY = new EntitlementTransitionPolicy();

    private final EntitlementRepository entitlementRepository = mock(EntitlementRepository.class);
    private final EntitlementGrantService grantService =
            new EntitlementGrantService(entitlementRepository);

    private UserEntity account;

    @BeforeEach
    void createAccount() {
        account = new UserEntity("kovo", "kovo@example.com", null, "Kovo", UserAccountStatus.ACTIVE);
        account.setId(ACCOUNT_ID);
    }

    @Nested
    @DisplayName("granting")
    class Granting {

        @Test
        @DisplayName("an account without an entitlement gets a fresh, unpaid, open-ended grant")
        void shouldWriteAGrantForAnAccountWithoutAnEntitlement() {
            storedEntitlement(null);

            final OperatorProDecisionOutcome outcome = grantService.grantPro(account, null);

            assertThat(outcome).isEqualTo(OperatorProDecisionOutcome.GRANTED);
            final EntitlementEntity saved = capturedSave();
            assertThat(saved.getOwner()).isSameAs(account);
            assertIsUnpaidActiveGrant(saved);
            assertThat(saved.getLastProviderEventAt()).isNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.kovospace.newtablinks.entitlement.services.EntitlementGrantServiceTest#lapsedEntitlements")
        @DisplayName("a lapsed paid row is replaced in place, keeping its newest provider event")
        void shouldReplaceALapsedRowInPlace(
                final String description, final Supplier<EntitlementEntity> lapsedRow) {

            final EntitlementEntity lapsed = lapsedRow.get();
            final Instant newestEventBefore = lapsed.getLastProviderEventAt();
            storedEntitlement(lapsed);

            final OperatorProDecisionOutcome outcome = grantService.grantPro(account, null);

            assertThat(outcome).isEqualTo(OperatorProDecisionOutcome.GRANTED);
            assertThat(capturedSave()).isSameAs(lapsed);
            assertIsUnpaidActiveGrant(lapsed);
            assertThat(lapsed.getLastProviderEventAt()).isEqualTo(newestEventBefore).isNotNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.kovospace.newtablinks.entitlement.services.EntitlementGrantServiceTest#standingEntitlements")
        @DisplayName("an account already pro, through any source, is left exactly as it is")
        void shouldLeaveAnAccountThatIsAlreadyProAlone(
                final String description, final Supplier<EntitlementEntity> standingRow) {

            final EntitlementEntity standing = standingRow.get();
            final EntitlementSource sourceBefore = standing.getSource();
            storedEntitlement(standing);

            final OperatorProDecisionOutcome outcome = grantService.grantPro(account, null);

            assertThat(outcome).isEqualTo(OperatorProDecisionOutcome.ALREADY_PRO);
            assertThat(standing.getSource()).isEqualTo(sourceBefore);
            verify(entitlementRepository, never()).save(any());
        }

        @Test
        @DisplayName("a one-year grant runs for one calendar year from now, in UTC")
        void shouldWriteAOneYearGrantEndingACalendarYearFromNow() {
            storedEntitlement(null);
            final Instant before = Instant.now();

            final OperatorProDecisionOutcome outcome =
                    grantService.grantPro(account, PremiumGrantTerm.ONE_YEAR);

            final Instant after = Instant.now();
            assertThat(outcome).isEqualTo(OperatorProDecisionOutcome.GRANTED);
            final EntitlementEntity saved = capturedSave();
            assertIsUnpaidActiveGrantUntil(saved, oneCalendarYearAfter(before), oneCalendarYearAfter(after));
        }

        @Test
        @DisplayName("an explicit lifetime grant has no end")
        void shouldWriteALifetimeGrantWithoutAnEnd() {
            storedEntitlement(null);

            final OperatorProDecisionOutcome outcome =
                    grantService.grantPro(account, PremiumGrantTerm.LIFETIME);

            assertThat(outcome).isEqualTo(OperatorProDecisionOutcome.GRANTED);
            assertIsUnpaidActiveGrant(capturedSave());
        }

        @Test
        @DisplayName("a one-year grant that has run out is replaced by a new grant, lifetime when no term is named")
        void shouldReplaceALapsedOneYearGrantWithANewGrant() {
            final EntitlementEntity lapsed = grantEndingIn(Duration.ofDays(-1));
            storedEntitlement(lapsed);

            final OperatorProDecisionOutcome outcome = grantService.grantPro(account, null);

            assertThat(outcome).isEqualTo(OperatorProDecisionOutcome.GRANTED);
            assertThat(capturedSave()).isSameAs(lapsed);
            assertIsUnpaidActiveGrant(lapsed);
        }

        @Test
        @DisplayName("a lifetime grant switched to one year now ends a calendar year from now")
        void shouldSwitchAStandingLifetimeGrantToOneYear() {
            final EntitlementEntity grant = pureGrant();
            storedEntitlement(grant);
            final Instant before = Instant.now();

            final OperatorProDecisionOutcome outcome =
                    grantService.grantPro(account, PremiumGrantTerm.ONE_YEAR);

            final Instant after = Instant.now();
            assertThat(outcome).isEqualTo(OperatorProDecisionOutcome.GRANT_TERM_REAPPLIED);
            assertThat(capturedSave()).isSameAs(grant);
            assertIsUnpaidActiveGrantUntil(grant, oneCalendarYearAfter(before), oneCalendarYearAfter(after));
        }

        @Test
        @DisplayName("a one-year grant switched to lifetime loses its end")
        void shouldSwitchAStandingOneYearGrantToLifetime() {
            final EntitlementEntity grant = grantEndingIn(Duration.ofDays(100));
            storedEntitlement(grant);

            final OperatorProDecisionOutcome outcome =
                    grantService.grantPro(account, PremiumGrantTerm.LIFETIME);

            assertThat(outcome).isEqualTo(OperatorProDecisionOutcome.GRANT_TERM_REAPPLIED);
            assertThat(capturedSave()).isSameAs(grant);
            assertIsUnpaidActiveGrant(grant);
        }

        @Test
        @DisplayName("a one-year grant named again is renewed for a calendar year from now")
        void shouldRenewAStandingOneYearGrantFromNow() {
            final EntitlementEntity grant = grantEndingIn(Duration.ofDays(30));
            storedEntitlement(grant);
            final Instant before = Instant.now();

            final OperatorProDecisionOutcome outcome =
                    grantService.grantPro(account, PremiumGrantTerm.ONE_YEAR);

            final Instant after = Instant.now();
            assertThat(outcome).isEqualTo(OperatorProDecisionOutcome.GRANT_TERM_REAPPLIED);
            assertIsUnpaidActiveGrantUntil(grant, oneCalendarYearAfter(before), oneCalendarYearAfter(after));
        }

        @Test
        @DisplayName("a standing one-year grant is left alone when no term is named, so saving the form does not extend it")
        void shouldLeaveAStandingOneYearGrantAloneWithoutATerm() {
            final Instant grantedUntil = Instant.now().plus(Duration.ofDays(30));
            final EntitlementEntity grant = newEntitlement();
            grant.becomeOperatorGrant(grantedUntil);
            storedEntitlement(grant);

            final OperatorProDecisionOutcome outcome = grantService.grantPro(account, null);

            assertThat(outcome).isEqualTo(OperatorProDecisionOutcome.ALREADY_PRO);
            assertThat(grant.getPaidUntil()).isEqualTo(grantedUntil);
            verify(entitlementRepository, never()).save(any());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(PremiumGrantTerm.class)
        @DisplayName("a standing purchase is never touched, whatever term is named")
        void shouldNeverTouchAStandingPurchaseWhateverTheTerm(final PremiumGrantTerm term) {
            for (final EntitlementEntity purchase
                    : new EntitlementEntity[] {activeSubscription(), standingLifetime()}) {
                final EntitlementSource sourceBefore = purchase.getSource();
                final Instant paidUntilBefore = purchase.getPaidUntil();
                storedEntitlement(purchase);

                assertThat(grantService.grantPro(account, term))
                        .isEqualTo(OperatorProDecisionOutcome.ALREADY_PRO);
                assertThat(purchase.getSource()).isEqualTo(sourceBefore);
                assertThat(purchase.getPaidUntil()).isEqualTo(paidUntilBefore);
            }
            verify(entitlementRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("revoking")
    class Revoking {

        @Test
        @DisplayName("a pure grant is deleted, so the account reads as never having had a plan")
        void shouldDeleteAPureGrant() {
            final EntitlementEntity grant = pureGrant();
            storedEntitlement(grant);

            final OperatorProDecisionOutcome outcome = grantService.revokeGrantedPro(account);

            assertThat(outcome).isEqualTo(OperatorProDecisionOutcome.REVOKED);
            verify(entitlementRepository).delete(grant);
        }

        @Test
        @DisplayName("a grant over provider history is kept but ended, so the stale guard survives")
        void shouldEndAGrantThatCarriesProviderHistoryInPlace() {
            final EntitlementEntity grant = expiredSubscription();
            grant.becomeOperatorGrant(null);
            storedEntitlement(grant);

            final OperatorProDecisionOutcome outcome = grantService.revokeGrantedPro(account);

            assertThat(outcome).isEqualTo(OperatorProDecisionOutcome.REVOKED);
            verify(entitlementRepository, never()).delete(any());
            assertThat(grant.getSource()).isEqualTo(EntitlementSource.GRANT);
            assertThat(grant.getStatus()).isEqualTo(EntitlementStatus.EXPIRED);
            assertThat(grant.grantsProAt(Instant.now())).isFalse();
            assertThat(grant.getLastProviderEventAt()).isNotNull();
        }

        @Test
        @DisplayName("a lifetime purchase cannot be revoked by the operator")
        void shouldRefuseToRevokeALifetimePurchase() {
            final EntitlementEntity lifetime = standingLifetime();
            storedEntitlement(lifetime);

            assertThatThrownBy(() -> grantService.revokeGrantedPro(account))
                    .isInstanceOf(PaidEntitlementRevocationException.class);

            assertThat(lifetime.getSource()).isEqualTo(EntitlementSource.LIFETIME);
            assertThat(lifetime.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
            verify(entitlementRepository, never()).delete(any());
        }

        @Test
        @DisplayName("a subscription still in its paid period cannot be revoked, even with its cancellation scheduled")
        void shouldRefuseToRevokeASubscriptionThatStillGrants() {
            final EntitlementEntity cancelledButPaid = activeSubscription();
            POLICY.apply(cancelledButPaid, false, subscriptionSignal(
                    EntitlementSignalKind.SUBSCRIPTION_CANCELLATION_SCHEDULED, OLD_SUBSCRIPTION,
                    Instant.now().minus(Duration.ofDays(1)), null));
            storedEntitlement(cancelledButPaid);

            assertThatThrownBy(() -> grantService.revokeGrantedPro(account))
                    .isInstanceOf(PaidEntitlementRevocationException.class);

            assertThat(cancelledButPaid.getStatus()).isEqualTo(EntitlementStatus.SCHEDULED_CANCEL);
            verify(entitlementRepository, never()).delete(any());
        }

        @Test
        @DisplayName("an account without an entitlement has nothing to take away")
        void shouldDoNothingForAnAccountWithoutAnEntitlement() {
            storedEntitlement(null);

            assertThat(grantService.revokeGrantedPro(account))
                    .isEqualTo(OperatorProDecisionOutcome.NOT_PRO);
            verify(entitlementRepository, never()).delete(any());
            verify(entitlementRepository, never()).save(any());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.kovospace.newtablinks.entitlement.services.EntitlementGrantServiceTest#lapsedEntitlements")
        @DisplayName("a lapsed paid row is not pro, so revoking leaves it untouched")
        void shouldLeaveALapsedPaidRowAlone(
                final String description, final Supplier<EntitlementEntity> lapsedRow) {

            final EntitlementEntity lapsed = lapsedRow.get();
            final EntitlementSource sourceBefore = lapsed.getSource();
            final EntitlementStatus statusBefore = lapsed.getStatus();
            storedEntitlement(lapsed);

            assertThat(grantService.revokeGrantedPro(account))
                    .isEqualTo(OperatorProDecisionOutcome.NOT_PRO);
            assertThat(lapsed.getSource()).isEqualTo(sourceBefore);
            assertThat(lapsed.getStatus()).isEqualTo(statusBefore);
            verify(entitlementRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("a payment arriving after a grant")
    class PaymentAfterGrant {

        @Test
        @DisplayName("a paid subscription takes the grant over")
        void shouldLetAPaidSubscriptionTakeOverAGrant() {
            final EntitlementEntity grant = pureGrant();
            final Instant paidUntil = Instant.now().plus(Duration.ofDays(365));

            final EntitlementSignalOutcome outcome = POLICY.apply(grant, false, subscriptionSignal(
                    EntitlementSignalKind.SUBSCRIPTION_PURCHASED, NEW_SUBSCRIPTION,
                    Instant.now(), paidUntil));

            assertThat(outcome).isEqualTo(EntitlementSignalOutcome.APPLIED);
            assertThat(grant.getSource()).isEqualTo(EntitlementSource.SUBSCRIPTION);
            assertThat(grant.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
            assertThat(grant.getPaidUntil()).isEqualTo(paidUntil);
            assertThat(grant.getProviderSubscriptionId()).isEqualTo("sub_new");
            assertThat(grant.getPaymentProvider()).isEqualTo(PaymentProvider.CREEM);
        }

        @Test
        @DisplayName("a lifetime purchase takes a one-year grant over and removes its end")
        void shouldLetALifetimePurchaseTakeOverAOneYearGrant() {
            final EntitlementEntity grant = grantEndingIn(Duration.ofDays(100));

            final EntitlementSignalOutcome outcome =
                    POLICY.apply(grant, false, lifetimeSignal(Instant.now()));

            assertThat(outcome).isEqualTo(EntitlementSignalOutcome.APPLIED);
            assertThat(grant.getSource()).isEqualTo(EntitlementSource.LIFETIME);
            assertThat(grant.getPaidUntil()).isNull();
            assertThat(grant.grantsProAt(Instant.now().plus(Duration.ofDays(1000)))).isTrue();
        }

        @Test
        @DisplayName("a subscription taking over a one-year grant does not inherit the grant's end")
        void shouldNotCarryAOneYearGrantsEndIntoASubscription() {
            final EntitlementEntity grant = grantEndingIn(Duration.ofDays(100));

            POLICY.apply(grant, false, subscriptionSignal(
                    EntitlementSignalKind.SUBSCRIPTION_PURCHASED, NEW_SUBSCRIPTION,
                    Instant.now(), null));

            assertThat(grant.getSource()).isEqualTo(EntitlementSource.SUBSCRIPTION);
            assertThat(grant.getPaidUntil()).isNull();
        }

        @Test
        @DisplayName("a lifetime purchase takes the grant over and schedules no cancellation")
        void shouldLetALifetimePurchaseTakeOverAGrantWithoutCancellingAnything() {
            final EntitlementEntity grant = pureGrant();

            final EntitlementSignalOutcome outcome =
                    POLICY.apply(grant, false, lifetimeSignal(Instant.now()));

            assertThat(outcome).isEqualTo(EntitlementSignalOutcome.APPLIED);
            assertThat(grant.getSource()).isEqualTo(EntitlementSource.LIFETIME);
            assertThat(grant.isStandingLifetimePurchase()).isTrue();
            assertThat(grant.pendingSupersededSubscriptionId()).isEmpty();
            assertThat(grant.getSupersededSubscriptionId()).isNull();
        }

        @Test
        @DisplayName("a late failure of the subscription the grant replaced does not touch it")
        void shouldIgnoreALateEventOfTheReplacedSubscription() {
            final EntitlementEntity grant = expiredSubscription();
            grant.becomeOperatorGrant(null);

            final EntitlementSignalOutcome outcome = POLICY.apply(grant, false, subscriptionSignal(
                    EntitlementSignalKind.SUBSCRIPTION_CANCELED, OLD_SUBSCRIPTION,
                    Instant.now(), null));

            assertThat(outcome).isEqualTo(EntitlementSignalOutcome.IGNORED_UNRELATED);
            assertIsUnpaidActiveGrant(grant);
        }

        @Test
        @DisplayName("an older event of the replaced subscription is still refused as stale")
        void shouldStillRefuseAnOlderEventOfTheReplacedSubscription() {
            final EntitlementEntity grant = expiredSubscription();
            grant.becomeOperatorGrant(null);

            final EntitlementSignalOutcome outcome = POLICY.apply(grant, false, subscriptionSignal(
                    EntitlementSignalKind.SUBSCRIPTION_PAID, OLD_SUBSCRIPTION,
                    Instant.now().minus(Duration.ofDays(400)),
                    Instant.now().plus(Duration.ofDays(1))));

            assertThat(outcome).isEqualTo(EntitlementSignalOutcome.IGNORED_STALE);
            assertIsUnpaidActiveGrant(grant);
        }

        @Test
        @DisplayName("a refund of the lifetime order the grant replaced does not revoke the grant")
        void shouldIgnoreARefundOfTheReplacedPurchase() {
            final EntitlementEntity grant = refundedLifetime();
            grant.becomeOperatorGrant(null);

            final EntitlementSignalOutcome outcome =
                    POLICY.apply(grant, false, refundOf(LIFETIME_ORDER, Instant.now()));

            assertThat(outcome).isEqualTo(EntitlementSignalOutcome.IGNORED_UNRELATED);
            assertIsUnpaidActiveGrant(grant);
        }
    }

    /**
     * Rows that once granted pro through a payment and no longer do.
     *
     * @return a description and a factory for each
     */
    static Stream<Arguments> lapsedEntitlements() {
        return Stream.of(
                Arguments.of("expired subscription",
                        (Supplier<EntitlementEntity>) EntitlementGrantServiceTest::expiredSubscription),
                Arguments.of("refunded lifetime purchase",
                        (Supplier<EntitlementEntity>) EntitlementGrantServiceTest::refundedLifetime),
                Arguments.of("cancelled subscription whose period ended",
                        (Supplier<EntitlementEntity>) EntitlementGrantServiceTest::cancelledAndEndedSubscription));
    }

    /**
     * Rows that grant pro right now, one per source.
     *
     * @return a description and a factory for each
     */
    static Stream<Arguments> standingEntitlements() {
        return Stream.of(
                Arguments.of("active subscription",
                        (Supplier<EntitlementEntity>) EntitlementGrantServiceTest::activeSubscription),
                Arguments.of("lifetime purchase",
                        (Supplier<EntitlementEntity>) EntitlementGrantServiceTest::standingLifetime),
                Arguments.of("operator grant",
                        (Supplier<EntitlementEntity>) EntitlementGrantServiceTest::pureGrant));
    }

    /**
     * Makes the repository hold the given row for the account, or none.
     *
     * @param entitlement the row, or {@code null} for none
     */
    private void storedEntitlement(final EntitlementEntity entitlement) {
        when(entitlementRepository.findByOwnerIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.ofNullable(entitlement));
    }

    /**
     * Returns the one entitlement the service saved.
     *
     * @return the saved row
     */
    private EntitlementEntity capturedSave() {
        final ArgumentCaptor<EntitlementEntity> saved =
                ArgumentCaptor.forClass(EntitlementEntity.class);
        verify(entitlementRepository).save(saved.capture());
        return saved.getValue();
    }

    /**
     * Asserts a row is a standing grant carrying nothing the grant CHECK constraint forbids, and
     * no purchase identifier a late event could match.
     *
     * @param entitlement the row
     */
    private static void assertIsUnpaidActiveGrant(final EntitlementEntity entitlement) {
        assertThat(entitlement.getPaidUntil()).isNull();
        assertCarriesNothingButAStandingGrant(entitlement);
    }

    /**
     * Asserts a row is a standing, unpaid grant ending within the given bounds.
     *
     * @param entitlement  the row
     * @param earliestEnd  the earliest acceptable end
     * @param latestEnd    the latest acceptable end
     */
    private static void assertIsUnpaidActiveGrantUntil(
            final EntitlementEntity entitlement,
            final Instant earliestEnd,
            final Instant latestEnd) {

        assertThat(entitlement.getPaidUntil()).isBetween(earliestEnd, latestEnd);
        assertCarriesNothingButAStandingGrant(entitlement);
    }

    /**
     * Asserts a row is an active grant with no payment column or purchase identifier set.
     *
     * @param entitlement the row
     */
    private static void assertCarriesNothingButAStandingGrant(final EntitlementEntity entitlement) {
        assertThat(entitlement.getSource()).isEqualTo(EntitlementSource.GRANT);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(entitlement.getPaymentProvider()).isNull();
        assertThat(entitlement.getChargedAmountInMinorUnits()).isNull();
        assertThat(entitlement.getChargedCurrency()).isNull();
        assertThat(entitlement.getProviderCustomerId()).isNull();
        assertThat(entitlement.getProviderSubscriptionId()).isNull();
        assertThat(entitlement.getProviderProductId()).isNull();
        assertThat(entitlement.getProviderOrderId()).isNull();
        assertThat(entitlement.grantsProAt(Instant.now())).isTrue();
    }

    /** @return a grant written onto a fresh row, with no provider history */
    private static EntitlementEntity pureGrant() {
        final EntitlementEntity grant = newEntitlement();
        grant.becomeOperatorGrant(null);
        return grant;
    }

    /**
     * Builds a grant on a fresh row ending at the given distance from now.
     *
     * @param untilEnd how far from now the grant ends; negative for one already over
     * @return the grant
     */
    private static EntitlementEntity grantEndingIn(final Duration untilEnd) {
        final EntitlementEntity grant = newEntitlement();
        grant.becomeOperatorGrant(Instant.now().plus(untilEnd));
        return grant;
    }

    /**
     * Adds one calendar year in UTC, the way a one-year grant counts it.
     *
     * @param moment the start
     * @return the same moment a year later
     */
    private static Instant oneCalendarYearAfter(final Instant moment) {
        return moment.atZone(ZoneOffset.UTC).plusYears(1).toInstant();
    }

    /** @return a subscription paid a month ago, good for most of a year */
    private static EntitlementEntity activeSubscription() {
        final EntitlementEntity entitlement = newEntitlement();
        POLICY.apply(entitlement, true, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_PURCHASED, OLD_SUBSCRIPTION,
                Instant.now().minus(Duration.ofDays(30)),
                Instant.now().plus(Duration.ofDays(335))));
        return entitlement;
    }

    /** @return a subscription whose paid period ended a month ago and then expired */
    private static EntitlementEntity expiredSubscription() {
        final EntitlementEntity entitlement = subscriptionThatEndedAMonthAgo();
        POLICY.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_EXPIRED, OLD_SUBSCRIPTION,
                Instant.now().minus(Duration.ofDays(30)), null));
        return entitlement;
    }

    /** @return a subscription cancelled outright whose paid period has since ended */
    private static EntitlementEntity cancelledAndEndedSubscription() {
        final EntitlementEntity entitlement = subscriptionThatEndedAMonthAgo();
        POLICY.apply(entitlement, false, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_CANCELED, OLD_SUBSCRIPTION,
                Instant.now().minus(Duration.ofDays(200)), null));
        return entitlement;
    }

    /** @return a subscription bought about 13 months ago whose year ran out a month ago */
    private static EntitlementEntity subscriptionThatEndedAMonthAgo() {
        final EntitlementEntity entitlement = newEntitlement();
        POLICY.apply(entitlement, true, subscriptionSignal(
                EntitlementSignalKind.SUBSCRIPTION_PURCHASED, OLD_SUBSCRIPTION,
                Instant.now().minus(Duration.ofDays(395)),
                Instant.now().minus(Duration.ofDays(30))));
        return entitlement;
    }

    /** @return a lifetime purchase that stands */
    private static EntitlementEntity standingLifetime() {
        final EntitlementEntity entitlement = newEntitlement();
        POLICY.apply(entitlement, true, lifetimeSignal(Instant.now().minus(Duration.ofDays(10))));
        return entitlement;
    }

    /** @return a lifetime purchase whose money went back */
    private static EntitlementEntity refundedLifetime() {
        final EntitlementEntity entitlement = standingLifetime();
        POLICY.apply(entitlement, false,
                refundOf(LIFETIME_ORDER, Instant.now().minus(Duration.ofDays(5))));
        return entitlement;
    }

    /**
     * A subscription signal.
     *
     * @param kind       what happened
     * @param references which subscription
     * @param occurredAt when
     * @param paidUntil  the reported period end, or {@code null}
     * @return the signal
     */
    private static EntitlementSignal subscriptionSignal(
            final EntitlementSignalKind kind,
            final ProviderPurchaseReferences references,
            final Instant occurredAt,
            final Instant paidUntil) {

        return new EntitlementSignal(kind, PaymentProvider.CREEM, occurredAt, null,
                references, paidUntil, YEARLY_PRICE);
    }

    /**
     * A lifetime purchase of the lifetime order.
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

    /** @return a brand-new entitlement for a throwaway account */
    private static EntitlementEntity newEntitlement() {
        return new EntitlementEntity(new UserEntity(
                "buyer", "buyer@example.com", null, "Buyer", UserAccountStatus.ACTIVE));
    }
}
