package com.kovospace.newtablinks.common.security;

import com.kovospace.newtablinks.common.exceptions.AuthenticationFailedException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Answers "who is making this request" from the validated access token.
 *
 * <p>This is the single place the rest of the application learns the caller's identity. Nothing
 * accepts an owner identifier as a request parameter any more: an identifier supplied by the
 * caller is a claim about who they would <em>like</em> to be, whereas the token subject is one
 * this service signed itself.</p>
 *
 * @since 0.0.2
 */
@Component
public class AuthenticatedUserProvider {

    /**
     * Returns the identifier of the user the current request is authenticated as.
     *
     * @return the authenticated user's identifier
     * @throws IllegalStateException         when called outside an authenticated request, which
     *                                       would be a wiring mistake rather than a client error
     * @throws AuthenticationFailedException when the token is valid but does not speak for a user
     */
    public UUID getAuthenticatedUserId() {
        final Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt accessToken)) {
            throw new IllegalStateException(
                    "No authenticated user in the security context; this endpoint should be "
                            + "protected by the security filter chain");
        }

        try {
            return UUID.fromString(accessToken.getSubject());
        } catch (final IllegalArgumentException subjectIsNotAUserIdentifier) {
            // This service signs more than one kind of token. An operator's admin token is valid,
            // and its subject is a username rather than a user identifier - so it authenticates
            // but speaks for nobody. Refusing it here is what stops an operator from acting *as*
            // a user; the uniform 401 says no more than every other rejected credential does.
            throw new AuthenticationFailedException();
        }
    }
}
