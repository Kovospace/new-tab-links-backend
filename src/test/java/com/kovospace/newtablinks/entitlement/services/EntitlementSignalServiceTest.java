package com.kovospace.newtablinks.entitlement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.entitlement.models.ChargedAmount;
import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignal;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalKind;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalOutcome;
import com.kovospace.newtablinks.entitlement.models.EntitlementSource;
import com.kovospace.newtablinks.entitlement.models.PaymentProvider;
import com.kovospace.newtablinks.entitlement.models.ProviderPurchaseReferences;
import com.kovospace.newtablinks.entitlement.repositories.EntitlementRepository;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.services.UserService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests how a payment signal is attributed to an account.
 *
 * @since 0.0.9
 */
class EntitlementSignalServiceTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-27T10:00:00Z");

    private final EntitlementRepository entitlementRepository = mock(EntitlementRepository.class);
    private final UserService userService = mock(UserService.class);
    private final EntitlementSignalService service = new EntitlementSignalService(
            entitlementRepository, new EntitlementTransitionPolicy(), userService);

    @Test
    @DisplayName("a payment naming an account in its metadata starts that account's entitlement")
    void shouldAttributeThroughTheAccountCarriedInCheckout() {
        final UserEntity account = accountWithId(ACCOUNT_ID);
        when(userService.findUserEntity(ACCOUNT_ID)).thenReturn(Optional.of(account));

        final EntitlementSignalOutcome outcome = service.applySignal(lifetimePurchase(ACCOUNT_ID));

        final ArgumentCaptor<EntitlementEntity> saved = ArgumentCaptor.forClass(EntitlementEntity.class);
        verify(entitlementRepository).saveAndFlush(saved.capture());
        assertThat(outcome).isEqualTo(EntitlementSignalOutcome.APPLIED);
        assertThat(saved.getValue().getOwner()).isSameAs(account);
        assertThat(saved.getValue().getSource()).isEqualTo(EntitlementSource.LIFETIME);
    }

    @Test
    @DisplayName("a signal carrying no account is attributed through the subscription on file")
    void shouldFallBackToTheSubscriptionOnFile() {
        final EntitlementEntity onFile = new EntitlementEntity(accountWithId(ACCOUNT_ID));
        when(entitlementRepository.findFirstByProviderSubscriptionId("sub_1"))
                .thenReturn(Optional.of(onFile));
        when(entitlementRepository.findByOwnerIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(onFile));

        final EntitlementSignalOutcome outcome = service.applySignal(new EntitlementSignal(
                EntitlementSignalKind.SUBSCRIPTION_PAID, PaymentProvider.CREEM, OCCURRED_AT, null,
                new ProviderPurchaseReferences("cust_1", "sub_1", null, null),
                OCCURRED_AT.plusSeconds(3600), new ChargedAmount(566, "EUR")));

        assertThat(outcome).isEqualTo(EntitlementSignalOutcome.APPLIED);
        assertThat(onFile.getProviderSubscriptionId()).isEqualTo("sub_1");
    }

    @Test
    @DisplayName("a payment for an account that no longer exists changes nothing")
    void shouldReportAPaymentForAMissingAccountAsUnattributed() {
        when(userService.findUserEntity(ACCOUNT_ID)).thenReturn(Optional.empty());

        final EntitlementSignalOutcome outcome = service.applySignal(lifetimePurchase(ACCOUNT_ID));

        assertThat(outcome).isEqualTo(EntitlementSignalOutcome.UNATTRIBUTED);
        verify(entitlementRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a payment with no account and no purchase on file changes nothing")
    void shouldReportAnAnonymousPaymentAsUnattributed() {
        final EntitlementSignalOutcome outcome = service.applySignal(lifetimePurchase(null));

        assertThat(outcome).isEqualTo(EntitlementSignalOutcome.UNATTRIBUTED);
        verify(entitlementRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a refund for an account with no entitlement creates nothing")
    void shouldNotCreateAnEntitlementFromARefund() {
        when(userService.findUserEntity(ACCOUNT_ID))
                .thenReturn(Optional.of(accountWithId(ACCOUNT_ID)));

        final EntitlementSignalOutcome outcome = service.applySignal(new EntitlementSignal(
                EntitlementSignalKind.PAYMENT_REFUNDED, PaymentProvider.CREEM, OCCURRED_AT,
                ACCOUNT_ID, new ProviderPurchaseReferences(null, null, null, "ord_1"), null, null));

        assertThat(outcome).isEqualTo(EntitlementSignalOutcome.IGNORED_UNRELATED);
        verify(entitlementRepository, never()).saveAndFlush(any());
    }

    /**
     * A lifetime purchase attributed to an account.
     *
     * @param accountId the account, or {@code null}
     * @return the signal
     */
    private static EntitlementSignal lifetimePurchase(final UUID accountId) {
        return new EntitlementSignal(EntitlementSignalKind.LIFETIME_PURCHASED,
                PaymentProvider.CREEM, OCCURRED_AT, accountId,
                new ProviderPurchaseReferences("cust_1", null, "prod_lifetime", "ord_1"),
                null, new ChargedAmount(1814, "EUR"));
    }

    /**
     * An account entity with a fixed identifier.
     *
     * @param accountId the identifier
     * @return the account
     */
    private static UserEntity accountWithId(final UUID accountId) {
        final UserEntity account = new UserEntity(
                "buyer", "buyer@example.com", null, "Buyer", UserAccountStatus.ACTIVE);
        account.setId(accountId);
        return account;
    }
}
