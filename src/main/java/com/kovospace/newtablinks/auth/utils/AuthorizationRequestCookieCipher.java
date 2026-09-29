package com.kovospace.newtablinks.auth.utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Seals the cookie that carries a provider sign-in across the redirect to Google and back, so
 * that the browser holding it can neither read nor alter it.
 *
 * <p>The cookie holds the PKCE code verifier and the OpenID nonce, which is why it is encrypted
 * rather than only signed, and it is authenticated because it comes back from a client: AES-GCM
 * does both. A value that fails authentication - tampered, truncated, or sealed under another
 * key - opens as empty, never as an exception, because a forged callback is an ordinary input and
 * not a fault.</p>
 *
 * <p>The key is derived from the JWT signing secret rather than configured separately: that
 * secret is already long, already comes from the secret store, and is already the same on every
 * replica - which is the whole point, since the pod that opens the cookie is often not the one
 * that sealed it. The derivation is HMAC-SHA256 under a fixed label, so the derived key is
 * unrelated to the signing key and can never be used to mint a token.</p>
 *
 * @since 0.0.12
 */
public final class AuthorizationRequestCookieCipher {

    /** Separates this key from every other use of the master secret; bump the version to rotate. */
    private static final byte[] KEY_DERIVATION_LABEL =
            "newtablinks/oauth2-authorization-request-cookie/v1".getBytes(StandardCharsets.UTF_8);

    private static final String KEY_DERIVATION_ALGORITHM = "HmacSHA256";
    private static final String ENCRYPTION_KEY_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    /** The initialisation vector length GCM is specified for; a fresh one for every seal. */
    private static final int INITIALISATION_VECTOR_BYTES = 12;

    /** The full GCM tag; a shorter one only makes forgery cheaper. */
    private static final int AUTHENTICATION_TAG_BITS = 128;

    private static final Base64.Encoder COOKIE_SAFE_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder COOKIE_SAFE_DECODER = Base64.getUrlDecoder();

    private final SecretKey encryptionKey;
    private final SecureRandom initialisationVectorSource = new SecureRandom();

    /**
     * Creates a cipher keyed from the service's master secret.
     *
     * @param masterSecret the JWT signing secret; every replica must be given the same one
     * @throws IllegalStateException when the JVM lacks HMAC-SHA256, which a compliant one cannot
     */
    public AuthorizationRequestCookieCipher(final String masterSecret) {
        this.encryptionKey = deriveEncryptionKey(masterSecret);
    }

    /**
     * Encrypts and authenticates a value for a cookie.
     *
     * @param plaintext what the cookie has to carry
     * @return a URL-safe Base64 string of the initialisation vector followed by the ciphertext
     * @throws IllegalStateException when the JVM lacks AES-GCM, which a compliant one cannot
     */
    public String seal(final byte[] plaintext) {
        final byte[] initialisationVector = new byte[INITIALISATION_VECTOR_BYTES];
        initialisationVectorSource.nextBytes(initialisationVector);
        try {
            final byte[] ciphertext = initialisedCipher(Cipher.ENCRYPT_MODE, initialisationVector)
                    .doFinal(plaintext);
            return COOKIE_SAFE_ENCODER.encodeToString(ByteBuffer
                    .allocate(initialisationVector.length + ciphertext.length)
                    .put(initialisationVector)
                    .put(ciphertext)
                    .array());
        } catch (final GeneralSecurityException impossibleOnAnyRealJvm) {
            throw new IllegalStateException(
                    CIPHER_TRANSFORMATION + " is required but failed", impossibleOnAnyRealJvm);
        }
    }

    /**
     * Decrypts a value this cipher sealed, provided nobody has altered it since.
     *
     * @param sealedValue the cookie value exactly as the browser returned it
     * @return the plaintext, or empty when the value is malformed, altered, or sealed under a
     *         different master secret
     */
    public Optional<byte[]> open(final String sealedValue) {
        final byte[] sealedBytes;
        try {
            sealedBytes = COOKIE_SAFE_DECODER.decode(sealedValue);
        } catch (final IllegalArgumentException notBase64) {
            return Optional.empty();
        }
        if (sealedBytes.length <= INITIALISATION_VECTOR_BYTES) {
            return Optional.empty();
        }
        return decrypt(sealedBytes);
    }

    /**
     * Separates the initialisation vector from the ciphertext and decrypts the latter.
     *
     * @param sealedBytes the decoded cookie, longer than an initialisation vector
     * @return the plaintext, or empty when authentication fails
     */
    private Optional<byte[]> decrypt(final byte[] sealedBytes) {
        final byte[] initialisationVector = Arrays.copyOf(sealedBytes, INITIALISATION_VECTOR_BYTES);
        final byte[] ciphertext =
                Arrays.copyOfRange(sealedBytes, INITIALISATION_VECTOR_BYTES, sealedBytes.length);
        try {
            return Optional.of(initialisedCipher(Cipher.DECRYPT_MODE, initialisationVector)
                    .doFinal(ciphertext));
        } catch (final GeneralSecurityException alteredOrSealedUnderAnotherKey) {
            return Optional.empty();
        }
    }

    /**
     * Prepares AES-GCM for one operation, bound to the key-derivation label as associated data.
     *
     * @param cipherMode           {@link Cipher#ENCRYPT_MODE} or {@link Cipher#DECRYPT_MODE}
     * @param initialisationVector the vector for this one value
     * @return the initialised cipher
     * @throws GeneralSecurityException when the JVM cannot provide or initialise AES-GCM
     */
    private Cipher initialisedCipher(final int cipherMode, final byte[] initialisationVector)
            throws GeneralSecurityException {

        final Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(cipherMode, encryptionKey,
                new GCMParameterSpec(AUTHENTICATION_TAG_BITS, initialisationVector));
        cipher.updateAAD(KEY_DERIVATION_LABEL);
        return cipher;
    }

    /**
     * Derives the 256-bit AES key as HMAC-SHA256 of the label under the master secret.
     *
     * @param masterSecret the JWT signing secret
     * @return the cookie encryption key
     * @throws IllegalStateException when the JVM lacks HMAC-SHA256
     */
    private static SecretKey deriveEncryptionKey(final String masterSecret) {
        try {
            final Mac keyDerivation = Mac.getInstance(KEY_DERIVATION_ALGORITHM);
            keyDerivation.init(new SecretKeySpec(
                    masterSecret.getBytes(StandardCharsets.UTF_8), KEY_DERIVATION_ALGORITHM));
            return new SecretKeySpec(
                    keyDerivation.doFinal(KEY_DERIVATION_LABEL), ENCRYPTION_KEY_ALGORITHM);
        } catch (final GeneralSecurityException impossibleOnAnyRealJvm) {
            throw new IllegalStateException(
                    KEY_DERIVATION_ALGORITHM + " is required but not available",
                    impossibleOnAnyRealJvm);
        }
    }
}
