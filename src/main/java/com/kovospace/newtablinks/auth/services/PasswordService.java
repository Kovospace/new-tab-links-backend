package com.kovospace.newtablinks.auth.services;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.auth.dtos.PasswordChangeRequestDto;
import com.kovospace.newtablinks.auth.dtos.PasswordResetConfirmationDto;
import com.kovospace.newtablinks.auth.models.EmailedTokenEntity;
import com.kovospace.newtablinks.auth.models.EmailedTokenPurpose;
import com.kovospace.newtablinks.auth.repositories.EmailedTokenRepository;
import com.kovospace.newtablinks.auth.repositories.RefreshTokenRepository;
import com.kovospace.newtablinks.auth.utils.SecureTokenGenerator;
import com.kovospace.newtablinks.auth.utils.TokenHasher;
import com.kovospace.newtablinks.common.exceptions.AuthenticationFailedException;
import com.kovospace.newtablinks.common.exceptions.InvalidTokenException;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Setting, changing and resetting a password.
 *
 * <p>All three end in the same place, and all three sign every device out afterwards. That is the
 * point of changing a password: if it is being changed because it leaked, leaving the sessions it
 * created alive would achieve nothing.</p>
 *
 * @since 0.0.3
 */
@Service
public class PasswordService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordService.class);

    private final UserRepository userRepository;
    private final EmailedTokenRepository emailedTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountEmailSender accountEmailSender;
    private final AuthenticationProperties authenticationProperties;
    private final WebApplicationProperties webApplicationProperties;

    /**
     * Creates the service.
     *
     * @param userRepository           reads and updates accounts
     * @param emailedTokenRepository   stores reset tokens
     * @param refreshTokenRepository   revokes sessions after a change
     * @param passwordEncoder          hashes and verifies passwords
     * @param accountEmailSender       delivers the reset link
     * @param authenticationProperties token lifetimes
     * @param webApplicationProperties builds the link into the website
     */
    public PasswordService(
            final UserRepository userRepository,
            final EmailedTokenRepository emailedTokenRepository,
            final RefreshTokenRepository refreshTokenRepository,
            final PasswordEncoder passwordEncoder,
            final AccountEmailSender accountEmailSender,
            final AuthenticationProperties authenticationProperties,
            final WebApplicationProperties webApplicationProperties) {

        this.userRepository = userRepository;
        this.emailedTokenRepository = emailedTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountEmailSender = accountEmailSender;
        this.authenticationProperties = authenticationProperties;
        this.webApplicationProperties = webApplicationProperties;
    }

    /**
     * Mails a reset link, if the address belongs to an account that can receive one.
     *
     * <p>Tells the caller nothing either way, for the same reason registration does not: an
     * endpoint that answers differently for known and unknown addresses is a way to enumerate
     * users.</p>
     *
     * <p>An account created through a provider that reported no address has an unroutable
     * placeholder, so no link can reach it - such an account can only gain a password while
     * signed in, through {@link #setOrChangePassword}.</p>
     *
     * @param emailAddress address to send the link to
     */
    @Transactional
    public void requestPasswordReset(final String emailAddress) {
        final Optional<UserEntity> account =
                userRepository.findByEmail(emailAddress.trim().toLowerCase(Locale.ROOT));

        if (account.isEmpty()) {
            LOGGER.debug("Password reset requested for an unknown address");
            return;
        }
        if (account.get().getStatus() != UserAccountStatus.ACTIVE) {
            LOGGER.debug("Password reset requested for account {} in status {}",
                    account.get().getId(), account.get().getStatus());
            return;
        }
        if (!isRoutable(account.get().getEmail())) {
            LOGGER.info("Password reset requested for account {}, whose address is a placeholder",
                    account.get().getId());
            return;
        }

        final String rawToken = SecureTokenGenerator.generateMachineToken();
        emailedTokenRepository.save(new EmailedTokenEntity(
                account.get(),
                TokenHasher.hash(rawToken),
                EmailedTokenPurpose.PASSWORD_RESET,
                Instant.now().plus(authenticationProperties.passwordResetTokenLifetime())));

        accountEmailSender.sendPasswordResetLink(
                account.get().getEmail(),
                account.get().getDisplayName(),
                webApplicationProperties.buildPasswordResetLink(rawToken));
    }

    /**
     * Sets a new password from a reset link and signs every device out.
     *
     * @param confirmation the token and the new password
     * @throws InvalidTokenException when the token is unknown, already used, or expired
     */
    @Transactional
    public void confirmPasswordReset(final PasswordResetConfirmationDto confirmation) {
        final EmailedTokenEntity resetToken = emailedTokenRepository
                .findByTokenHashAndPurpose(
                        TokenHasher.hash(confirmation.token()), EmailedTokenPurpose.PASSWORD_RESET)
                .orElseThrow(() -> new InvalidTokenException(
                        "That reset link is not valid. Request a new one."));

        final Instant now = Instant.now();
        if (!resetToken.isUsableAt(now)) {
            throw new InvalidTokenException(
                    "That reset link has already been used or has expired. Request a new one.");
        }

        resetToken.markConsumed(now);

        final UserEntity account = resetToken.getUser();
        account.setPasswordHash(passwordEncoder.encode(confirmation.newPassword()));
        account.resetFailedLoginAttempts();

        revokeEveryDevice(account.getId(), "password reset");
    }

    /**
     * Sets or changes the password of the signed-in user, then signs every device out.
     *
     * <p>When the account has no password yet - which is every account created through Google -
     * {@code currentPassword} is not required, because there is nothing for it to prove. When the
     * account does have one, it must be correct.</p>
     *
     * @param userId        identifier of the signed-in user
     * @param changeRequest the current password, if any, and the new one
     * @throws AuthenticationFailedException when the account has a password and the supplied
     *                                       current one does not match
     */
    @Transactional
    public void setOrChangePassword(
            final UUID userId,
            final PasswordChangeRequestDto changeRequest) {

        final UserEntity account = userRepository.findById(userId)
                .orElseThrow(AuthenticationFailedException::new);

        if (account.hasPassword()) {
            if (changeRequest.currentPassword() == null
                    || !passwordEncoder.matches(
                            changeRequest.currentPassword(), account.getPasswordHash())) {

                LOGGER.debug("Password change refused for account {}: current password wrong",
                        userId);
                throw new AuthenticationFailedException();
            }
        } else {
            LOGGER.info("Account {} is setting a password for the first time", userId);
        }

        account.setPasswordHash(passwordEncoder.encode(changeRequest.newPassword()));
        account.resetFailedLoginAttempts();

        revokeEveryDevice(userId, "password change");
    }

    /**
     * Revokes every live session of an account.
     *
     * @param userId identifier of the account
     * @param reason wording for the log line
     */
    private void revokeEveryDevice(final UUID userId, final String reason) {
        final int revoked = refreshTokenRepository.revokeAllLiveTokensOfUser(userId, Instant.now());
        LOGGER.info("Account {}: {} revoked {} session(s)", userId, reason, revoked);
    }

    /**
     * Tells whether mail can actually reach an address.
     *
     * @param emailAddress address to judge
     * @return {@code false} for the synthetic placeholder given to provider accounts that
     *         reported no address
     */
    private static boolean isRoutable(final String emailAddress) {
        return !emailAddress.endsWith("@no-address.invalid");
    }
}
