package com.kovospace.newtablinks.auth.services;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.dtos.ExtensionConnectCodeDto;
import com.kovospace.newtablinks.auth.dtos.TokenPairDto;
import com.kovospace.newtablinks.auth.models.SingleUseCodeEntity;
import com.kovospace.newtablinks.auth.models.SingleUseCodePurpose;
import com.kovospace.newtablinks.auth.repositories.SingleUseCodeRepository;
import com.kovospace.newtablinks.auth.utils.SecureTokenGenerator;
import com.kovospace.newtablinks.auth.utils.TokenHasher;
import com.kovospace.newtablinks.common.exceptions.InvalidTokenException;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mints and redeems the two kinds of single use code this service hands out.
 *
 * <p>The connect code is what lets a user who signed up with Google use the browser extension
 * without ever having a password: they read a short code off the website and type it into the
 * extension, which trades it for a normal token pair. From that point the extension is an
 * ordinary client and knows nothing about how the account was created.</p>
 *
 * @since 0.0.2
 */
@Service
public class SingleUseCodeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleUseCodeService.class);

    /**
     * Blocks in a connect code, and characters per block - {@code 4F2K-9QX1}.
     *
     * <p>Two blocks of four from a 30 character alphabet is about 39 bits. That is weak in
     * isolation and entirely sufficient here, because the code dies within minutes, works once,
     * and belongs to one account.</p>
     */
    private static final int CONNECT_CODE_BLOCKS = 2;
    private static final int CONNECT_CODE_CHARS_PER_BLOCK = 4;

    private final SingleUseCodeRepository singleUseCodeRepository;
    private final TokenPairFactory tokenPairFactory;
    private final AuthenticationProperties authenticationProperties;

    /**
     * Creates the service.
     *
     * @param singleUseCodeRepository  stores minted codes
     * @param tokenPairFactory         issues tokens on redemption
     * @param authenticationProperties code lifetimes
     */
    public SingleUseCodeService(
            final SingleUseCodeRepository singleUseCodeRepository,
            final TokenPairFactory tokenPairFactory,
            final AuthenticationProperties authenticationProperties) {

        this.singleUseCodeRepository = singleUseCodeRepository;
        this.tokenPairFactory = tokenPairFactory;
        this.authenticationProperties = authenticationProperties;
    }

    /**
     * Mints the code the website hands the user to connect their browser extension.
     *
     * @param account account the code will sign the extension in as
     * @return the code to display, and when it stops working
     */
    @Transactional
    public ExtensionConnectCodeDto mintExtensionConnectCode(final UserEntity account) {
        final String code = SecureTokenGenerator.generateHumanReadableCode(
                CONNECT_CODE_BLOCKS, CONNECT_CODE_CHARS_PER_BLOCK);

        final Instant expiresAt =
                Instant.now().plus(authenticationProperties.extensionConnectLifetime());

        singleUseCodeRepository.save(new SingleUseCodeEntity(
                account,
                TokenHasher.hash(SecureTokenGenerator.normaliseHumanReadableCode(code)),
                SingleUseCodePurpose.EXTENSION_CONNECT,
                expiresAt));

        LOGGER.info("Minted an extension connect code for account {}", account.getId());
        return new ExtensionConnectCodeDto(code, expiresAt);
    }

    /**
     * Mints the code handed to the website at the end of a provider sign-in.
     *
     * @param account account that just signed in
     * @return the raw code to place in the redirect
     */
    @Transactional
    public String mintWebSessionHandoffCode(final UserEntity account) {
        final String code = SecureTokenGenerator.generateMachineToken();

        singleUseCodeRepository.save(new SingleUseCodeEntity(
                account,
                TokenHasher.hash(code),
                SingleUseCodePurpose.WEB_SESSION_HANDOFF,
                Instant.now().plus(authenticationProperties.webSessionHandoffLifetime())));

        return code;
    }

    /**
     * Redeems a code for a token pair, spending it in the process.
     *
     * @param submittedCode     the code as received or typed
     * @param purpose           exchange being attempted; a code minted for the other one is refused
     * @param clientDescription description of the redeeming client, may be {@code null}
     * @return a fresh token pair
     * @throws InvalidTokenException when the code is unknown, spent, expired, or its account can
     *                               no longer sign in
     */
    @Transactional
    public TokenPairDto redeemCode(
            final String submittedCode,
            final SingleUseCodePurpose purpose,
            final String clientDescription) {

        final String lookupValue = purpose == SingleUseCodePurpose.EXTENSION_CONNECT
                ? SecureTokenGenerator.normaliseHumanReadableCode(submittedCode)
                : submittedCode.trim();

        final SingleUseCodeEntity code = singleUseCodeRepository
                .findByCodeHashAndPurpose(TokenHasher.hash(lookupValue), purpose)
                .orElseThrow(() -> new InvalidTokenException(
                        "That code is not valid. Generate a new one."));

        final Instant now = Instant.now();
        if (!code.isRedeemableAt(now)) {
            throw new InvalidTokenException(
                    "That code has already been used or has expired. Generate a new one.");
        }

        final UserEntity account = code.getUser();
        if (!account.isActive()) {
            throw new InvalidTokenException("That code is not valid. Generate a new one.");
        }

        code.markConsumed(now);
        LOGGER.info("Redeemed a {} code for account {}", purpose, account.getId());
        return tokenPairFactory.issueTokenPairFor(account, clientDescription);
    }
}
