package com.kovospace.newtablinks.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.auth.dtos.RegistrationRequestDto;
import com.kovospace.newtablinks.auth.models.AccountEmailDeliveryOutcome;
import com.kovospace.newtablinks.auth.repositories.EmailedTokenRepository;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Verifies that registration reports how far its message got, and that both of its branches
 * report it the same way.
 *
 * <p>That symmetry is the whole reason the outcome may be shown to the caller at all: a value
 * that appeared on one branch and not the other would turn the response into an answer to
 * "is this address registered?", which registration exists to refuse.</p>
 *
 * @since 0.0.5
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final String USERNAME = "someone";
    private static final String EMAIL = "someone@example.com";
    private static final String DISPLAY_NAME = "Someone";
    private static final String PASSWORD = "a-password-nobody-guesses";
    private static final String ACTIVATION_LINK = "https://example.test/activate?token=whatever";

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailedTokenRepository emailedTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccountEmailSender accountEmailSender;

    @Mock
    private AuthenticationProperties authenticationProperties;

    @Mock
    private WebApplicationProperties webApplicationProperties;

    @InjectMocks
    private RegistrationService registrationService;

    @Test
    @DisplayName("reports a failed relay when a new account's activation link could not be sent")
    void shouldReportDeliveryFailureWhenActivationLinkCannotBeSent() {
        givenTheAddressIsFree();
        when(accountEmailSender.sendActivationLink(anyString(), anyString(), anyString()))
                .thenReturn(AccountEmailDeliveryOutcome.FAILED);

        final AccountEmailDeliveryOutcome outcome = registrationService.register(aRegistration());

        assertThat(outcome).isEqualTo(AccountEmailDeliveryOutcome.FAILED);
        assertThat(outcome.isFailure()).isTrue();
    }

    @Test
    @DisplayName("reports success when a new account's activation link reached the relay")
    void shouldReportDeliverySuccessWhenActivationLinkIsSent() {
        givenTheAddressIsFree();
        when(accountEmailSender.sendActivationLink(anyString(), anyString(), anyString()))
                .thenReturn(AccountEmailDeliveryOutcome.SENT);

        assertThat(registrationService.register(aRegistration()))
                .isEqualTo(AccountEmailDeliveryOutcome.SENT);
    }

    @Test
    @DisplayName("reports a failed relay identically for an address that is already registered")
    void shouldReportDeliveryFailureForAnAlreadyRegisteredAddressToo() {
        when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);
        when(accountEmailSender.sendAddressAlreadyRegisteredNotice(EMAIL))
                .thenReturn(AccountEmailDeliveryOutcome.FAILED);

        final AccountEmailDeliveryOutcome outcome = registrationService.register(aRegistration());

        assertThat(outcome).isEqualTo(AccountEmailDeliveryOutcome.FAILED);
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(accountEmailSender, never()).sendActivationLink(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("treats mail being switched off as nothing having gone wrong")
    void shouldNotReportSuppressedMailAsAFailure() {
        givenTheAddressIsFree();
        when(accountEmailSender.sendActivationLink(anyString(), anyString(), anyString()))
                .thenReturn(AccountEmailDeliveryOutcome.SUPPRESSED);

        assertThat(registrationService.register(aRegistration()).isFailure()).isFalse();
    }

    /**
     * Stubs the repository and the token settings for an address nobody has registered.
     */
    private void givenTheAddressIsFree() {
        when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticationProperties.activationTokenLifetime()).thenReturn(Duration.ofHours(24));
        when(webApplicationProperties.buildActivationLink(anyString())).thenReturn(ACTIVATION_LINK);
    }

    /**
     * Builds a registration request that passes validation.
     *
     * <p>The password is repeated because the confirmation field has to match; a request whose
     * two passwords differ never reaches the service under test.</p>
     *
     * @return a well formed request
     */
    private RegistrationRequestDto aRegistration() {
        return new RegistrationRequestDto(USERNAME, EMAIL, PASSWORD, PASSWORD, DISPLAY_NAME);
    }
}
