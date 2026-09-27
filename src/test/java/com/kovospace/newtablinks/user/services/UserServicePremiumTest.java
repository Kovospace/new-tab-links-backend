package com.kovospace.newtablinks.user.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.common.services.AccountDeletionService;
import com.kovospace.newtablinks.entitlement.services.EntitlementStandingService;
import com.kovospace.newtablinks.user.mappers.UserMapperImpl;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins that the account a client reads carries the server's judgement of premium.
 *
 * @since 0.0.9
 */
class UserServicePremiumTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final EntitlementStandingService entitlementStandingService =
            mock(EntitlementStandingService.class);
    private final UserService userService = new UserService(
            userRepository,
            new UserMapperImpl(),
            mock(AccountDeletionService.class),
            entitlementStandingService);

    @ParameterizedTest(name = "premium {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("the account carries premium exactly as the entitlement judges it")
    void shouldCarryPremiumAsJudgedByEntitlement(final boolean accountIsPro) {
        final UserEntity account = new UserEntity(
                "buyer", "buyer@example.com", null, "Buyer", UserAccountStatus.ACTIVE);
        account.setId(ACCOUNT_ID);
        when(userRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(entitlementStandingService.isAccountProNow(ACCOUNT_ID)).thenReturn(accountIsPro);

        assertThat(userService.findUserById(ACCOUNT_ID).premium()).isEqualTo(accountIsPro);
    }
}
