package com.kovospace.newtablinks.auth.services;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.dtos.TokenPairDto;
import com.kovospace.newtablinks.auth.models.RefreshTokenEntity;
import com.kovospace.newtablinks.auth.repositories.RefreshTokenRepository;
import com.kovospace.newtablinks.auth.utils.SecureTokenGenerator;
import com.kovospace.newtablinks.auth.utils.TokenHasher;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a proven identity into the token pair a client actually uses.
 *
 * <p>Every way of proving identity - password, provider sign-in, connect code, refresh - ends
 * here, so the shape and lifetime of a session is decided in exactly one place.</p>
 *
 * @since 0.0.2
 */
@Service
public class TokenPairFactory {

    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationProperties authenticationProperties;

    /**
     * Creates the factory.
     *
     * @param accessTokenIssuer        mints signed access tokens
     * @param refreshTokenRepository   stores the refresh half
     * @param authenticationProperties token lifetimes
     */
    public TokenPairFactory(
            final AccessTokenIssuer accessTokenIssuer,
            final RefreshTokenRepository refreshTokenRepository,
            final AuthenticationProperties authenticationProperties) {

        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authenticationProperties = authenticationProperties;
    }

    /**
     * Issues a fresh pair and records the refresh half against the account.
     *
     * @param user              account the pair speaks for
     * @param clientDescription description of the client, may be {@code null}
     * @return the issued pair; the refresh token is returned here and never again
     */
    @Transactional
    public TokenPairDto issueTokenPairFor(final UserEntity user, final String clientDescription) {
        final String rawRefreshToken = SecureTokenGenerator.generateMachineToken();

        refreshTokenRepository.save(new RefreshTokenEntity(
                user,
                TokenHasher.hash(rawRefreshToken),
                Instant.now().plus(authenticationProperties.refreshTokenLifetime()),
                clientDescription));

        return new TokenPairDto(
                accessTokenIssuer.issueAccessTokenFor(user),
                accessTokenIssuer.getAccessTokenLifetimeSeconds(),
                rawRefreshToken,
                user.getId(),
                user.getUsername());
    }
}
