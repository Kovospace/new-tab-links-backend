package com.kovospace.newtablinks.admin.services;

import com.kovospace.newtablinks.admin.config.AdminAccessProperties;
import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Mints the short-lived token the operator sends on every admin request.
 *
 * <p>Signed with the same key as a user's access token, and validated by the same resource
 * server, but it is deliberately a different <em>kind</em> of token. Its subject is the admin
 * username rather than a user identifier, and it carries a {@code scope} of {@code ADMIN}, which
 * Spring Security turns into the {@code SCOPE_ADMIN} authority that the admin endpoints demand.
 * A user's token has no scope claim and therefore no authority, so it can never reach them.</p>
 *
 * <p>The reverse direction is closed too, and matters more: an admin token presented to an
 * ordinary endpoint carries a subject that is not a user identifier, which
 * {@link com.kovospace.newtablinks.common.security.AuthenticatedUserProvider} refuses. An
 * operator can therefore administer accounts but cannot act <em>as</em> one.</p>
 *
 * <p><strong>There is no refresh token.</strong> An admin session is meant to be short, and a
 * long-lived credential for this identity is exactly what should not exist - when the token
 * expires the operator signs in again.</p>
 *
 * @since 0.0.6
 */
@Service
public class AdminAccessTokenIssuer {

    /**
     * Claim Spring Security reads to build authorities. A space-delimited {@code scope} claim
     * becomes one {@code SCOPE_}-prefixed authority per entry, with no configuration on our side.
     */
    static final String SCOPE_CLAIM = "scope";

    /**
     * The single scope this service grants. Named as a capability rather than a role because it
     * is exactly one thing: reach the endpoints under {@code /api/v1/admin}.
     */
    public static final String ADMIN_SCOPE = "ADMIN";

    /**
     * Authority the scope above produces, and the one the admin endpoints require.
     */
    public static final String ADMIN_AUTHORITY = "SCOPE_" + ADMIN_SCOPE;

    private final JwtEncoder jwtEncoder;
    private final AdminAccessProperties adminAccessProperties;
    private final AuthenticationProperties authenticationProperties;

    /**
     * Creates the issuer.
     *
     * @param jwtEncoder               encoder configured with this service's signing key
     * @param adminAccessProperties    the admin identity and how long its token lives
     * @param authenticationProperties supplies the issuer identity every token here carries
     */
    public AdminAccessTokenIssuer(
            final JwtEncoder jwtEncoder,
            final AdminAccessProperties adminAccessProperties,
            final AuthenticationProperties authenticationProperties) {

        this.jwtEncoder = jwtEncoder;
        this.adminAccessProperties = adminAccessProperties;
        this.authenticationProperties = authenticationProperties;
    }

    /**
     * Issues a token for the operator.
     *
     * @return the serialised, signed token
     */
    public String issueAdminAccessToken() {
        final Instant issuedAt = Instant.now();

        final JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authenticationProperties.jwtIssuer())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(adminAccessProperties.tokenLifetime()))
                .subject(adminAccessProperties.username())
                .claim(SCOPE_CLAIM, ADMIN_SCOPE)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /**
     * Returns the moment a token issued now would expire.
     *
     * @return the expiry moment, for the client to show and to pre-empt
     */
    public Instant expiryOfATokenIssuedNow() {
        return Instant.now().plus(adminAccessProperties.tokenLifetime());
    }
}
