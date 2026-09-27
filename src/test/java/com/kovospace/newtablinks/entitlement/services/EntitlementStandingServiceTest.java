package com.kovospace.newtablinks.entitlement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.EntitlementStatus;
import com.kovospace.newtablinks.entitlement.models.PaymentProvider;
import com.kovospace.newtablinks.entitlement.models.ProviderPurchaseReferences;
import com.kovospace.newtablinks.entitlement.repositories.EntitlementRepository;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins which entitlements make an account premium.
 *
 * @since 0.0.9
 */
class EntitlementStandingServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
    private static final ProviderPurchaseReferences PURCHASE =
            new ProviderPurchaseReferences("cust_1", "sub_1", "prod_1", "ord_1");

    private final EntitlementRepository entitlementRepository = mock(EntitlementRepository.class);
    private final EntitlementStandingService entitlementStandingService =
            new EntitlementStandingService(entitlementRepository);

    @Test
    @DisplayName("a standing lifetime purchase is premium")
    void shouldTreatLifetimePurchaseAsPremium() {
        final EntitlementEntity lifetime = newEntitlement();
        lifetime.recordLifetimePurchase(PaymentProvider.CREEM, PURCHASE, null);

        assertThat(isPremiumWith(lifetime)).isTrue();
    }

    @Test
    @DisplayName("an active subscription inside its paid period is premium")
    void shouldTreatActiveSubscriptionAsPremium() {
        assertThat(isPremiumWith(subscription(EntitlementStatus.ACTIVE))).isTrue();
    }

    @Test
    @DisplayName("an expired subscription is not premium")
    void shouldNotTreatExpiredSubscriptionAsPremium() {
        assertThat(isPremiumWith(subscription(EntitlementStatus.EXPIRED))).isFalse();
    }

    @Test
    @DisplayName("a refunded lifetime purchase is not premium")
    void shouldNotTreatRefundedPurchaseAsPremium() {
        final EntitlementEntity refunded = newEntitlement();
        refunded.recordLifetimePurchase(PaymentProvider.CREEM, PURCHASE, null);
        refunded.changeStatus(EntitlementStatus.REFUNDED);

        assertThat(isPremiumWith(refunded)).isFalse();
    }

    @Test
    @DisplayName("an account without an entitlement is not premium")
    void shouldNotTreatAccountWithoutEntitlementAsPremium() {
        when(entitlementRepository.findByOwnerId(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThat(entitlementStandingService.isAccountProAt(ACCOUNT_ID, NOW)).isFalse();
    }

    /**
     * Asks the service about an account holding the given entitlement.
     *
     * @param entitlement what the account holds
     * @return whether the service calls it premium at {@link #NOW}
     */
    private boolean isPremiumWith(final EntitlementEntity entitlement) {
        when(entitlementRepository.findByOwnerId(ACCOUNT_ID)).thenReturn(Optional.of(entitlement));
        return entitlementStandingService.isAccountProAt(ACCOUNT_ID, NOW);
    }

    /**
     * Builds a subscription paid until a month after {@link #NOW}.
     *
     * @param status where it stands
     * @return the entitlement
     */
    private static EntitlementEntity subscription(final EntitlementStatus status) {
        final EntitlementEntity subscription = newEntitlement();
        subscription.attachToSubscription(PaymentProvider.CREEM, PURCHASE);
        subscription.changeStatus(status);
        subscription.replacePaidUntilWhenReported(NOW.plus(Duration.ofDays(30)));
        return subscription;
    }

    /**
     * Starts an entitlement for a fresh account.
     *
     * @return the entitlement
     */
    private static EntitlementEntity newEntitlement() {
        return new EntitlementEntity(new UserEntity(
                "buyer", "buyer@example.com", null, "Buyer", UserAccountStatus.ACTIVE));
    }
}
