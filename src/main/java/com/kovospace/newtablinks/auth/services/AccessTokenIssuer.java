package com.kovospace.newtablinks.auth.services;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.time.Instant;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

/**
 * Mints the signed access tokens clients send on every request.
 *
 * <p>The token carries the user's identifier as its subject, and nothing that could go stale.
 * Display name, email and status are deliberately left out: a token lives for minutes and is not
 * a place to cache facts that a request could otherwise read fresh from the database.</p>
 *
 * @since 0.0.2
 */
@Service
public class AccessTokenIssuer {

    /**
     * Claim carrying the username, purely so a client can render "signed in as …" without an
     * extra request. Never used for authorization.
     */
    static final String USERNAME_CLAIM = "username";

    private final JwtEncoder jwtEncoder;
    private final AuthenticationProperties authenticationProperties;

    /**
     * Creates the issuer.
     *
     * @param jwtEncoder               encoder configured with this service's signing key
     * @param authenticationProperties lifetimes and issuer identity
     */
    public AccessTokenIssuer(
            final JwtEncoder jwtEncoder,
            final AuthenticationProperties authenticationProperties) {

        this.jwtEncoder = jwtEncoder;
        this.authenticationProperties = authenticationProperties;
    }

    /**
     * Issues an access token for a user.
     *
     * @param user account the token speaks for
     * @return the serialised, signed token
     */
    public String issueAccessTokenFor(final UserEntity user) {
        final Instant issuedAt = Instant.now();

        final JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authenticationProperties.jwtIssuer())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(authenticationProperties.accessTokenLifetime()))
                .subject(user.getId().toString())
                .claim(USERNAME_CLAIM, user.getUsername())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /**
     * Returns how long a freshly issued access token remains valid.
     *
     * @return the access token lifetime in seconds
     */
    public long getAccessTokenLifetimeSeconds() {
        return authenticationProperties.accessTokenLifetime().toSeconds();
    }
}
