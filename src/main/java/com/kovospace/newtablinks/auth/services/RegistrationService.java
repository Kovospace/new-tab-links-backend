package com.kovospace.newtablinks.auth.services;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.auth.dtos.RegistrationRequestDto;
import com.kovospace.newtablinks.auth.models.EmailedTokenEntity;
import com.kovospace.newtablinks.auth.models.EmailedTokenPurpose;
import com.kovospace.newtablinks.auth.repositories.EmailedTokenRepository;
import com.kovospace.newtablinks.auth.utils.SecureTokenGenerator;
import com.kovospace.newtablinks.auth.utils.TokenHasher;
import com.kovospace.newtablinks.common.exceptions.InvalidTokenException;
import com.kovospace.newtablinks.common.exceptions.RegistrationConflictException;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Classic registration: username, address and password, proven by a link sent in the post.
 *
 * @since 0.0.2
 */
@Service
public class RegistrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository userRepository;
    private final EmailedTokenRepository emailedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountEmailSender accountEmailSender;
    private final AuthenticationProperties authenticationProperties;
    private final WebApplicationProperties webApplicationProperties;

    /**
     * Creates the service.
     *
     * @param userRepository            stores accounts
     * @param emailedTokenRepository stores activation tokens
     * @param passwordEncoder           hashes chosen passwords
     * @param accountEmailSender     delivers the activation link
     * @param authenticationProperties  token lifetimes
     * @param webApplicationProperties  builds the link into the website
     */
    public RegistrationService(
            final UserRepository userRepository,
            final EmailedTokenRepository emailedTokenRepository,
            final PasswordEncoder passwordEncoder,
            final AccountEmailSender accountEmailSender,
            final AuthenticationProperties authenticationProperties,
            final WebApplicationProperties webApplicationProperties) {

        this.userRepository = userRepository;
        this.emailedTokenRepository = emailedTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountEmailSender = accountEmailSender;
        this.authenticationProperties = authenticationProperties;
        this.webApplicationProperties = webApplicationProperties;
    }

    /**
     * Registers an account, or quietly does nothing if the address is already taken.
     *
     * <p>The caller cannot tell those two apart, by design - see
     * {@link com.kovospace.newtablinks.common.exceptions.RegistrationConflictException}. A taken
     * username, being public information, is refused openly.</p>
     *
     * @param registrationRequest what the user submitted
     * @throws RegistrationConflictException when the username is already taken
     */
    @Transactional
    public void register(final RegistrationRequestDto registrationRequest) {
        final String normalisedEmail = normaliseEmail(registrationRequest.email());

        if (userRepository.existsByUsername(registrationRequest.username())) {
            throw new RegistrationConflictException("That username is already taken");
        }

        if (userRepository.existsByEmail(normalisedEmail)) {
            LOGGER.info("Registration attempted for an address that is already registered");
            accountEmailSender.sendAddressAlreadyRegisteredNotice(normalisedEmail);
            return;
        }

        final UserEntity newUser = userRepository.save(new UserEntity(
                registrationRequest.username(),
                normalisedEmail,
                passwordEncoder.encode(registrationRequest.password()),
                registrationRequest.displayName(),
                UserAccountStatus.PENDING_ACTIVATION));

        mintAndSendActivationToken(newUser);
    }

    /**
     * Tells whether a username is already registered.
     *
     * <p>Exists so the website's registration form can say "taken" while the user is still
     * typing, instead of only after they submit. It answers the same question
     * {@link #register(com.kovospace.newtablinks.auth.dtos.RegistrationRequestDto)} answers with
     * a 409, and discloses nothing that a registration attempt would not disclose anyway - a
     * username is public by nature, which is why a taken one is refused openly.</p>
     *
     * <p>The comparison is exact, matching the uniqueness rule the database enforces. A name that
     * differs only in letter case is therefore reported as free, and registering it would
     * succeed.</p>
     *
     * @param username the name to look up, already validated against the registration constraints
     * @return {@code true} when an account already uses that name
     */
    @Transactional(readOnly = true)
    public boolean isUsernameTaken(final String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Sends a fresh activation link, if the address belongs to an account still awaiting one.
     *
     * <p>Answers nothing to the caller in any case; the result is only ever visible in the inbox.
     * That keeps the endpoint from becoming a way to test which addresses are registered.</p>
     *
     * @param emailAddress address to resend to
     */
    @Transactional
    public void resendActivationLink(final String emailAddress) {
        final Optional<UserEntity> account = userRepository.findByEmail(normaliseEmail(emailAddress));

        if (account.isEmpty() || account.get().getStatus() != UserAccountStatus.PENDING_ACTIVATION) {
            LOGGER.debug("Activation resend requested for an address with nothing pending");
            return;
        }
        mintAndSendActivationToken(account.get());
    }

    /**
     * Activates the account behind an activation token.
     *
     * @param rawActivationToken the token taken from the activation link
     * @throws InvalidTokenException when the token is unknown, already used, or expired
     */
    @Transactional
    public void activate(final String rawActivationToken) {
        final EmailedTokenEntity activationToken = emailedTokenRepository
                .findByTokenHashAndPurpose(
                        TokenHasher.hash(rawActivationToken), EmailedTokenPurpose.ACCOUNT_ACTIVATION)
                .orElseThrow(() -> new InvalidTokenException(
                        "That activation link is not valid. Request a new one."));

        final Instant now = Instant.now();
        if (!activationToken.isUsableAt(now)) {
            throw new InvalidTokenException(
                    "That activation link has already been used or has expired. Request a new one.");
        }

        activationToken.markConsumed(now);

        final UserEntity account = activationToken.getUser();
        if (account.getStatus() == UserAccountStatus.PENDING_ACTIVATION) {
            account.setStatus(UserAccountStatus.ACTIVE);
            LOGGER.info("Account {} activated", account.getId());
        }
    }

    /**
     * Mints an activation token for an account and mails the link.
     *
     * @param account account awaiting activation
     */
    private void mintAndSendActivationToken(final UserEntity account) {
        final String rawToken = SecureTokenGenerator.generateMachineToken();

        emailedTokenRepository.save(new EmailedTokenEntity(
                account,
                TokenHasher.hash(rawToken),
                EmailedTokenPurpose.ACCOUNT_ACTIVATION,
                Instant.now().plus(authenticationProperties.activationTokenLifetime())));

        accountEmailSender.sendActivationLink(
                account.getEmail(),
                account.getDisplayName(),
                webApplicationProperties.buildActivationLink(rawToken));
    }

    /**
     * Puts an address into the single form it is stored and compared in.
     *
     * <p>Only case is normalised. The local part of an address is case sensitive by
     * specification, but no mail provider in practice treats it that way, and users routinely
     * capitalise inconsistently. Anything cleverer - stripping dots, cutting {@code +tags} - is
     * provider specific and would wrongly merge distinct addresses.</p>
     *
     * @param emailAddress address as submitted
     * @return the normalised address
     */
    private static String normaliseEmail(final String emailAddress) {
        return emailAddress.trim().toLowerCase(Locale.ROOT);
    }
}
