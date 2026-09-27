package com.kovospace.newtablinks.payment.utils;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Computes and checks hex-encoded HMAC-SHA256 signatures over raw bytes.
 *
 * <p>Works on bytes end to end on purpose. The signature covers the request body exactly as sent;
 * decoding it into a {@code String} first - with a charset guessed from a header the provider
 * does not send - changes every non-ASCII byte and breaks verification for exactly the customers
 * with an accent in their name.</p>
 *
 * @since 0.0.9
 */
public final class HmacSha256Signatures {

    /** The JCA name of the algorithm. */
    private static final String ALGORITHM = "HmacSHA256";

    /** Length of an HMAC-SHA256 digest, in bytes. */
    private static final int SIGNATURE_LENGTH_IN_BYTES = 32;

    /**
     * Prevents instantiation of this utility.
     */
    private HmacSha256Signatures() {
    }

    /**
     * Computes the signature of a message.
     *
     * @param secret  the shared secret
     * @param message the exact bytes that were signed
     * @return the signature, as lower-case hex
     */
    public static String signToHex(final byte[] secret, final byte[] message) {
        return HexFormat.of().formatHex(sign(secret, message));
    }

    /**
     * Tells whether a presented hex signature is the signature of a message.
     *
     * <p>The comparison is constant-time ({@link MessageDigest#isEqual(byte[], byte[])} on the
     * decoded bytes), so the time it takes says nothing about how many leading bytes were right.
     * A missing, blank, non-hex or wrongly sized value is simply not a match.</p>
     *
     * @param secret             the shared secret
     * @param message            the exact bytes that were signed
     * @param presentedSignature the signature the caller sent, hex, either case; may be
     *                           {@code null}
     * @return {@code true} only for the correct signature
     */
    public static boolean isValidHexSignature(
            final byte[] secret,
            final byte[] message,
            final String presentedSignature) {

        if (presentedSignature == null || presentedSignature.isBlank()) {
            return false;
        }
        final byte[] presentedBytes;
        try {
            presentedBytes = HexFormat.of()
                    .parseHex(presentedSignature.strip().toLowerCase(Locale.ROOT));
        } catch (final IllegalArgumentException notHexadecimal) {
            return false;
        }
        if (presentedBytes.length != SIGNATURE_LENGTH_IN_BYTES) {
            return false;
        }
        return MessageDigest.isEqual(sign(secret, message), presentedBytes);
    }

    /**
     * Computes the raw signature of a message.
     *
     * @param secret  the shared secret
     * @param message the exact bytes that were signed
     * @return the 32-byte digest
     */
    private static byte[] sign(final byte[] secret, final byte[] message) {
        try {
            final Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(message);
        } catch (final NoSuchAlgorithmException | InvalidKeyException unavailable) {
            // HmacSHA256 is mandatory on every Java platform, and any non-empty key is valid.
            throw new IllegalStateException("HMAC-SHA256 is unavailable", unavailable);
        }
    }
}
