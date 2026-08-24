package com.kovospace.newtablinks.auth.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Hashes the tokens this service stores, so the database never holds a usable secret.
 *
 * <p>Activation tokens, refresh tokens and connect codes are all bearer secrets: whoever holds
 * one can act as the user. Storing them verbatim means a database leak is immediately an account
 * takeover. Storing the hash means a leak yields nothing usable, exactly as with passwords.</p>
 *
 * <p>Plain SHA-256 is correct here and a password hash would be wrong. These tokens are generated
 * by {@link SecureTokenGenerator} with full entropy, so there is no dictionary to attack and
 * nothing for a slow, salted hash to defend against - it would only make every request slower.
 * Passwords, which humans choose, are the opposite case and use
 * {@link org.springframework.security.crypto.password.PasswordEncoder} instead.</p>
 *
 * @since 0.0.2
 */
public final class TokenHasher {

    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * Not instantiable; this class only holds static helpers.
     */
    private TokenHasher() {
        throw new AssertionError("TokenHasher is a utility class and must not be instantiated");
    }

    /**
     * Hashes a token for storage or lookup.
     *
     * @param rawToken the secret as it was handed to the client
     * @return the Base64 encoded SHA-256 digest of the token
     * @throws IllegalStateException when the JVM lacks SHA-256, which cannot happen on a
     *                               specification compliant platform
     */
    public static String hash(final String rawToken) {
        try {
            final MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return Base64.getEncoder().encodeToString(
                    digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException impossibleOnAnyRealJvm) {
            throw new IllegalStateException(
                    HASH_ALGORITHM + " is required but not available", impossibleOnAnyRealJvm);
        }
    }
}
