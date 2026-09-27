package com.kovospace.newtablinks.user.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.common.exceptions.PaidEntitlementRevocationException;
import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.EntitlementSource;
import com.kovospace.newtablinks.entitlement.models.EntitlementStatus;
import com.kovospace.newtablinks.entitlement.models.OperatorProDecisionOutcome;
import com.kovospace.newtablinks.entitlement.models.PaymentProvider;
import com.kovospace.newtablinks.entitlement.models.ProviderPurchaseReferences;
import com.kovospace.newtablinks.entitlement.repositories.EntitlementRepository;
import com.kovospace.newtablinks.entitlement.services.EntitlementGrantService;
import com.kovospace.newtablinks.entitlement.services.EntitlementStandingService;
import com.kovospace.newtablinks.user.dtos.AdminUserCreateRequestDto;
import com.kovospace.newtablinks.user.dtos.AdminUserDto;
import com.kovospace.newtablinks.user.dtos.AdminUserPageDto;
import com.kovospace.newtablinks.user.dtos.AdminUserUpdateRequestDto;
import com.kovospace.newtablinks.user.mappers.AdminUserMapperImpl;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Pins how the operator's account screens read and change premium: the list reads the whole
 * page's standing in one query, and the checkbox reaches the grant service only when sent.
 *
 * @since 0.0.10
 */
class UserAdministrationServicePremiumTest {

    private static final UUID LIFETIME_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GRANTED_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID LAPSED_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PLAIN_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000004");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final EntitlementRepository entitlementRepository = mock(EntitlementRepository.class);
    private final EntitlementGrantService entitlementGrantService =
            mock(EntitlementGrantService.class);
    private final UserAdministrationService administrationService = new UserAdministrationService(
            userRepository,
            new AdminUserMapperImpl(),
            mock(PasswordEncoder.class),
            new EntitlementStandingService(entitlementRepository),
            entitlementGrantService);

    @Test
    @DisplayName("the list reads the whole page's entitlements in one query")
    void shouldReadThePagesEntitlementsInOneQuery() {
        final UserEntity lifetimeAccount = account(LIFETIME_ACCOUNT_ID, "lifetime");
        final UserEntity grantedAccount = account(GRANTED_ACCOUNT_ID, "granted");
        final UserEntity lapsedAccount = account(LAPSED_ACCOUNT_ID, "lapsed");
        final UserEntity plainAccount = account(PLAIN_ACCOUNT_ID, "plain");
        when(userRepository.searchAccounts(anyString(), any())).thenReturn(new PageImpl<>(
                List.of(lifetimeAccount, grantedAccount, lapsedAccount, plainAccount),
                PageRequest.of(0, 25), 4));
        when(entitlementRepository.findAllByOwnerIdIn(anyCollection())).thenReturn(List.of(
                lifetimeOf(lifetimeAccount), grantOf(grantedAccount), refundedLifetimeOf(lapsedAccount)));

        final AdminUserPageDto page = administrationService.listAccounts("", 0, 25);

        assertThat(page.users()).extracting(AdminUserDto::premium)
                .containsExactly(true, true, false, false);
        assertThat(page.users()).extracting(AdminUserDto::premiumSource)
                .containsExactly(EntitlementSource.LIFETIME, EntitlementSource.GRANT, null, null);
        verify(entitlementRepository, times(1)).findAllByOwnerIdIn(List.of(
                LIFETIME_ACCOUNT_ID, GRANTED_ACCOUNT_ID, LAPSED_ACCOUNT_ID, PLAIN_ACCOUNT_ID));
        verify(entitlementRepository, never()).findByOwnerId(any());
    }

    @Test
    @DisplayName("an update without premium leaves pro alone, so an older client revokes nothing")
    void shouldLeavePremiumAloneWhenTheUpdateOmitsIt() {
        final UserEntity grantedAccount = storedAccount(GRANTED_ACCOUNT_ID, "granted");
        when(entitlementRepository.findByOwnerId(GRANTED_ACCOUNT_ID))
                .thenReturn(Optional.of(grantOf(grantedAccount)));

        final AdminUserDto updated = administrationService.updateAccount(
                GRANTED_ACCOUNT_ID, updateRequest("granted", null));

        verify(entitlementGrantService, never()).grantProUnlessAlreadyPro(any());
        verify(entitlementGrantService, never()).revokeGrantedPro(any());
        assertThat(updated.premium()).isTrue();
        assertThat(updated.premiumSource()).isEqualTo(EntitlementSource.GRANT);
    }

    @Test
    @DisplayName("an update with premium true asks for a grant and answers the new standing")
    void shouldGrantWhenTheUpdateAsksForPremium() {
        final UserEntity plainAccount = storedAccount(PLAIN_ACCOUNT_ID, "plain");
        when(entitlementGrantService.grantProUnlessAlreadyPro(plainAccount))
                .thenReturn(OperatorProDecisionOutcome.GRANTED);
        when(entitlementRepository.findByOwnerId(PLAIN_ACCOUNT_ID))
                .thenReturn(Optional.of(grantOf(plainAccount)));

        final AdminUserDto updated = administrationService.updateAccount(
                PLAIN_ACCOUNT_ID, updateRequest("plain", true));

        verify(entitlementGrantService).grantProUnlessAlreadyPro(plainAccount);
        assertThat(updated.premium()).isTrue();
        assertThat(updated.premiumSource()).isEqualTo(EntitlementSource.GRANT);
    }

    @Test
    @DisplayName("a refused revocation propagates before any other field is changed")
    void shouldChangeNothingWhenTheRevocationIsRefused() {
        final UserEntity lifetimeAccount = storedAccount(LIFETIME_ACCOUNT_ID, "lifetime");
        when(entitlementGrantService.revokeGrantedPro(lifetimeAccount))
                .thenThrow(new PaidEntitlementRevocationException("paid"));

        assertThatThrownBy(() -> administrationService.updateAccount(
                LIFETIME_ACCOUNT_ID, new AdminUserUpdateRequestDto(
                        "changed@example.com", "Changed", UserAccountStatus.DISABLED, false)))
                .isInstanceOf(PaidEntitlementRevocationException.class);

        assertThat(lifetimeAccount.getEmail()).isEqualTo("lifetime@example.com");
        assertThat(lifetimeAccount.getDisplayName()).isEqualTo("lifetime");
        assertThat(lifetimeAccount.getStatus()).isEqualTo(UserAccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("creating with premium grants the new account; without it, nothing is granted")
    void shouldGrantANewAccountOnlyWhenAsked() {
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            final UserEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(entitlementGrantService.grantProUnlessAlreadyPro(any()))
                .thenReturn(OperatorProDecisionOutcome.GRANTED);

        administrationService.createAccount(createRequest("withpro", true));
        administrationService.createAccount(createRequest("withoutpro", false));

        verify(entitlementGrantService, times(1)).grantProUnlessAlreadyPro(
                argThat(created ->
                        "withpro".equals(created.getUsername())));
        verify(entitlementGrantService, never()).revokeGrantedPro(any());
    }

    /**
     * An account the repository returns by its identifier.
     *
     * @param id       its identifier
     * @param username its username
     * @return the account
     */
    private UserEntity storedAccount(final UUID id, final String username) {
        final UserEntity stored = account(id, username);
        when(userRepository.findById(eq(id))).thenReturn(Optional.of(stored));
        return stored;
    }

    /**
     * An account with a fixed identifier.
     *
     * @param id       its identifier
     * @param username its username, also its display name
     * @return the account
     */
    private static UserEntity account(final UUID id, final String username) {
        final UserEntity account = new UserEntity(
                username, username + "@example.com", null, username, UserAccountStatus.ACTIVE);
        account.setId(id);
        return account;
    }

    /**
     * An update that keeps the account's fields and sends the given premium.
     *
     * @param username the account's username, from which its email and display name derive
     * @param premium  the premium to send, or {@code null} to omit it
     * @return the request
     */
    private static AdminUserUpdateRequestDto updateRequest(
            final String username, final Boolean premium) {

        return new AdminUserUpdateRequestDto(
                username + "@example.com", username, UserAccountStatus.ACTIVE, premium);
    }

    /**
     * A creation request.
     *
     * @param username the new account's username
     * @param premium  whether to grant pro
     * @return the request
     */
    private static AdminUserCreateRequestDto createRequest(
            final String username, final boolean premium) {

        return new AdminUserCreateRequestDto(username, username + "@example.com", username,
                null, UserAccountStatus.ACTIVE, premium);
    }

    /**
     * A standing lifetime purchase.
     *
     * @param owner the account
     * @return the entitlement
     */
    private static EntitlementEntity lifetimeOf(final UserEntity owner) {
        final EntitlementEntity entitlement = new EntitlementEntity(owner);
        entitlement.recordLifetimePurchase(PaymentProvider.CREEM,
                new ProviderPurchaseReferences("cust", null, "prod", "ord"), null);
        return entitlement;
    }

    /**
     * A lifetime purchase that was refunded.
     *
     * @param owner the account
     * @return the entitlement
     */
    private static EntitlementEntity refundedLifetimeOf(final UserEntity owner) {
        final EntitlementEntity entitlement = lifetimeOf(owner);
        entitlement.changeStatus(EntitlementStatus.REFUNDED);
        return entitlement;
    }

    /**
     * An operator grant.
     *
     * @param owner the account
     * @return the entitlement
     */
    private static EntitlementEntity grantOf(final UserEntity owner) {
        final EntitlementEntity entitlement = new EntitlementEntity(owner);
        entitlement.becomeOperatorGrant();
        return entitlement;
    }
}
