package com.kovospace.newtablinks.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.user.models.AuthenticationProviderType;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.models.UserIdentityEntity;
import com.kovospace.newtablinks.user.repositories.UserIdentityRepository;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies how a provider sign-in is matched to a local account.
 *
 * <p>This path cannot be exercised over HTTP without live Google credentials, and it is the one
 * place where a mistake silently creates duplicate accounts or, worse, hands an existing account
 * to the wrong person - so it is covered here instead.</p>
 *
 * @since 0.0.2
 */
@ExtendWith(MockitoExtension.class)
class ProviderSignInServiceTest {

    private static final AuthenticationProviderType GOOGLE = AuthenticationProviderType.GOOGLE;
    private static final String PROVIDER_SUBJECT = "google-subject-12345";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @InjectMocks
    private ProviderSignInService providerSignInService;

    @Test
    @DisplayName("a returning user is recognised by the provider subject, not by their address")
    void shouldReturnTheLinkedAccountWhenTheIdentityIsAlreadyKnown() {
        final UserEntity existingAccount = anAccount("kovo", "kovo@example.com");
        when(userIdentityRepository.findByProviderAndProviderUserId(GOOGLE, PROVIDER_SUBJECT))
                .thenReturn(Optional.of(new UserIdentityEntity(
                        existingAccount, GOOGLE, PROVIDER_SUBJECT, "old@example.com")));

        final UserEntity resolved = providerSignInService.resolveAccountForProviderSignIn(
                GOOGLE, PROVIDER_SUBJECT, "changed@example.com", true, "Matej");

        assertThat(resolved).isSameAs(existingAccount);
        // A changed address must not create or re-match an account.
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("a verified address links the provider to the existing local account")
    void shouldLinkToAnExistingAccountWhenTheProviderVerifiedTheAddress() {
        final UserEntity existingAccount = anAccount("kovo", "kovo@example.com");
        when(userIdentityRepository.findByProviderAndProviderUserId(GOOGLE, PROVIDER_SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("kovo@example.com")).thenReturn(Optional.of(existingAccount));

        final UserEntity resolved = providerSignInService.resolveAccountForProviderSignIn(
                GOOGLE, PROVIDER_SUBJECT, "Kovo@Example.com", true, "Matej");

        assertThat(resolved).isSameAs(existingAccount);
        verify(userIdentityRepository).save(any(UserIdentityEntity.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("an UNVERIFIED address must never take over an existing account")
    void shouldNotLinkToAnExistingAccountWhenTheAddressIsNotVerified() {
        when(userIdentityRepository.findByProviderAndProviderUserId(GOOGLE, PROVIDER_SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));

        providerSignInService.resolveAccountForProviderSignIn(
                GOOGLE, PROVIDER_SUBJECT, "kovo@example.com", false, "Matej");

        // The existing account is never even looked up, so it cannot be claimed.
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("a new account from a provider is active immediately and has no password")
    void shouldCreateAnActivePasswordlessAccountForANewIdentity() {
        when(userIdentityRepository.findByProviderAndProviderUserId(GOOGLE, PROVIDER_SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("newcomer@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));

        final ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);

        providerSignInService.resolveAccountForProviderSignIn(
                GOOGLE, PROVIDER_SUBJECT, "newcomer@example.com", true, "Newcomer");

        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        assertThat(saved.getValue().hasPassword()).isFalse();
        assertThat(saved.getValue().getEmail()).isEqualTo("newcomer@example.com");
        assertThat(saved.getValue().getUsername()).isEqualTo("newcomer");
    }

    @Test
    @DisplayName("a taken derived username is suffixed until it is free")
    void shouldSuffixTheDerivedUsernameUntilItIsAvailable() {
        when(userIdentityRepository.findByProviderAndProviderUserId(GOOGLE, PROVIDER_SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("kovo@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("kovo")).thenReturn(true);
        when(userRepository.existsByUsername("kovo-1")).thenReturn(true);
        when(userRepository.existsByUsername("kovo-2")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));

        final ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);

        providerSignInService.resolveAccountForProviderSignIn(
                GOOGLE, PROVIDER_SUBJECT, "kovo@example.com", true, "Matej");

        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("kovo-2");
    }

    @Test
    @DisplayName("a provider that returns no address still yields a usable account")
    void shouldCreateAnAccountWhenTheProviderReturnsNoAddress() {
        when(userIdentityRepository.findByProviderAndProviderUserId(GOOGLE, PROVIDER_SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));

        final ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);

        providerSignInService.resolveAccountForProviderSignIn(
                GOOGLE, PROVIDER_SUBJECT, null, false, null);

        verify(userRepository).save(saved.capture());
        // The placeholder must be unroutable, so it can never receive mail or collide.
        assertThat(saved.getValue().getEmail()).endsWith("@no-address.invalid");
        assertThat(saved.getValue().getDisplayName()).isNotBlank();
    }

    /**
     * Builds a persisted-looking account for a test.
     *
     * @param username sign-in name
     * @param email    address
     * @return the account
     */
    private static UserEntity anAccount(final String username, final String email) {
        final UserEntity account = new UserEntity(
                username, email, "{bcrypt}$2a$10$abcdefghijklmnopqrstuv", "Test", UserAccountStatus.ACTIVE);
        account.setId(java.util.UUID.randomUUID());
        return account;
    }
}
