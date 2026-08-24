package com.kovospace.newtablinks.auth.services;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.dtos.ClientDescriptionDto;
import com.kovospace.newtablinks.auth.dtos.TokenPairDto;
import com.kovospace.newtablinks.auth.models.RefreshTokenEntity;
import com.kovospace.newtablinks.auth.repositories.RefreshTokenRepository;
import com.kovospace.newtablinks.auth.utils.SecureTokenGenerator;
import com.kovospace.newtablinks.auth.utils.TokenHasher;
import com.kovospace.newtablinks.user.models.UserDeviceEntity;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.services.UserDeviceService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a proven identity into the token pair a client actually uses.
 *
 * <p>Every way of proving identity - password, provider sign-in, connect code, refresh - ends
 * here, so the shape and lifetime of a session is decided in exactly one place, and every
 * session is attributed to a device.</p>
 *
 * @since 0.0.2
 */
@Service
public class TokenPairFactory {

    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDeviceService userDeviceService;
    private final AuthenticationProperties authenticationProperties;

    /**
     * Creates the factory.
     *
     * @param accessTokenIssuer        mints signed access tokens
     * @param refreshTokenRepository   stores the refresh half
     * @param userDeviceService        attributes the session to a device
     * @param authenticationProperties token lifetimes
     */
    public TokenPairFactory(
            final AccessTokenIssuer accessTokenIssuer,
            final RefreshTokenRepository refreshTokenRepository,
            final UserDeviceService userDeviceService,
            final AuthenticationProperties authenticationProperties) {

        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userDeviceService = userDeviceService;
        this.authenticationProperties = authenticationProperties;
    }

    /**
     * Issues a fresh pair, recording the device it was issued to.
     *
     * @param user              account the pair speaks for
     * @param clientDescription where the request is coming from
     * @return the issued pair; the refresh token is returned here and never again
     */
    @Transactional
    public TokenPairDto issueTokenPairFor(
            final UserEntity user,
            final ClientDescriptionDto clientDescription) {

        return issueTokenPairForDevice(
                user, userDeviceService.recordDeviceUse(user, clientDescription));
    }

    /**
     * Issues a fresh pair for a device that is already known.
     *
     * <p>Used when refreshing: the device was resolved from the presented token, so looking it up
     * again from request headers would be both wasteful and wrong - a client that changed its
     * reported device name mid-session would otherwise silently spawn a second device.</p>
     *
     * @param user   account the pair speaks for
     * @param device device the pair is issued to
     * @return the issued pair
     */
    @Transactional
    public TokenPairDto issueTokenPairForDevice(
            final UserEntity user,
            final UserDeviceEntity device) {

        final String rawRefreshToken = SecureTokenGenerator.generateMachineToken();

        refreshTokenRepository.save(new RefreshTokenEntity(
                user,
                device,
                TokenHasher.hash(rawRefreshToken),
                Instant.now().plus(authenticationProperties.refreshTokenLifetime())));

        return new TokenPairDto(
                accessTokenIssuer.issueAccessTokenFor(user),
                accessTokenIssuer.getAccessTokenLifetimeSeconds(),
                rawRefreshToken,
                user.getId(),
                user.getUsername());
    }
}
