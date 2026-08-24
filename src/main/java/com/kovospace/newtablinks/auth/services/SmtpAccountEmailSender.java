package com.kovospace.newtablinks.auth.services;

import com.kovospace.newtablinks.auth.config.MailProperties;
import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends account mail through the configured SMTP relay.
 *
 * <p>When {@code newtablinks.mail.enabled} is {@code false} nothing is transmitted and the
 * message is written to the log instead, activation link included. That is what lets the whole
 * registration flow be developed and tested before a mail provider exists, and it is why the flag
 * defaults to off.</p>
 *
 * <p>A failure to send never fails the surrounding request. Registration has already succeeded by
 * the time mail is attempted, and rolling it back because a relay was briefly unreachable would
 * lose the account and tell the caller something it must not learn. The user resends instead.</p>
 *
 * @since 0.0.2
 */
@Service
public class SmtpAccountEmailSender implements AccountEmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpAccountEmailSender.class);

    private final JavaMailSender javaMailSender;
    private final MailProperties mailProperties;
    private final WebApplicationProperties webApplicationProperties;

    /**
     * Creates the sender.
     *
     * @param javaMailSender           configured SMTP transport
     * @param mailProperties           sender identity and the enable flag
     * @param webApplicationProperties used to point the recipient at the website
     */
    public SmtpAccountEmailSender(
            final JavaMailSender javaMailSender,
            final MailProperties mailProperties,
            final WebApplicationProperties webApplicationProperties) {

        this.javaMailSender = javaMailSender;
        this.mailProperties = mailProperties;
        this.webApplicationProperties = webApplicationProperties;
    }

    @Override
    public void sendActivationLink(
            final String recipientAddress,
            final String displayName,
            final String activationLink) {

        sendOrLog(
                recipientAddress,
                "Activate your NewTabLinks account",
                """
                Hello %s,

                Use the link below to activate your NewTabLinks account:

                %s

                If you did not create this account, ignore this message and nothing will happen.
                """.formatted(displayName, activationLink));
    }

    @Override
    public void sendPasswordResetLink(
            final String recipientAddress,
            final String displayName,
            final String passwordResetLink) {

        sendOrLog(
                recipientAddress,
                "Reset your NewTabLinks password",
                """
                Hello %s,

                Use the link below to choose a new NewTabLinks password:

                %s

                Setting a new password signs you out everywhere, on every device.

                If you did not ask for this, ignore this message - your password has not
                changed, and nobody can change it without this link.
                """.formatted(displayName, passwordResetLink));
    }

    @Override
    public void sendAddressAlreadyRegisteredNotice(final String recipientAddress) {
        sendOrLog(
                recipientAddress,
                "Someone tried to register your address",
                """
                Hello,

                Somebody just tried to create a NewTabLinks account with this address, but it is
                already registered. No new account was created and nothing has changed.

                If that was you, sign in at %s instead, or reset your password from there.
                """.formatted(webApplicationProperties.baseUrl()));
    }

    /**
     * Transmits a message, or logs it when sending is switched off.
     *
     * @param recipientAddress address to send to
     * @param subject          subject line
     * @param body             plain text body
     */
    private void sendOrLog(final String recipientAddress, final String subject, final String body) {
        if (!mailProperties.enabled()) {
            LOGGER.info("""
                    Mail sending is disabled; the message below was NOT transmitted.
                    To: {}
                    Subject: {}
                    {}""", recipientAddress, subject, body);
            return;
        }

        try {
            final MimeMessage message = javaMailSender.createMimeMessage();
            final MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailProperties.fromAddress(), mailProperties.fromName());
            helper.setTo(recipientAddress);
            helper.setSubject(subject);
            helper.setText(body, false);
            javaMailSender.send(message);
            LOGGER.debug("Sent \"{}\" to {}", subject, recipientAddress);
        } catch (final MailException | MessagingException | UnsupportedEncodingException sendingFailed) {
            LOGGER.error("Could not send \"{}\" to {}", subject, recipientAddress, sendingFailed);
        }
    }
}
