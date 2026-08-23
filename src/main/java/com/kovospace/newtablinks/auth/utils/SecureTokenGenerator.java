package com.kovospace.newtablinks.auth.utils;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Produces the random secrets this service hands out - activation tokens, refresh tokens and the
 * short code that connects a browser extension to an account.
 *
 * <p>Everything here comes from {@link SecureRandom}. A predictable token is the same as no token
 * at all, so {@link java.util.Random} must never be used for any of these.</p>
 *
 * @since 0.0.2
 */
public final class SecureTokenGenerator {

    /**
     * Alphabet for codes a human retypes.
     *
     * <p>Deliberately excludes {@code I}, {@code L}, {@code O}, {@code U}, {@code 0} and
     * {@code 1}: they are the characters people confuse when copying a code off one screen and
     * into another, and {@code U} is dropped so the alphabet cannot spell unfortunate words.</p>
     */
    private static final char[] HUMAN_READABLE_ALPHABET =
            "ABCDEFGHJKMNPQRSTVWXYZ23456789".toCharArray();

    /**
     * Bytes of entropy behind a machine-handled token. 32 bytes is 256 bits.
     */
    private static final int MACHINE_TOKEN_ENTROPY_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Base64.Encoder URL_SAFE_ENCODER = Base64.getUrlEncoder().withoutPadding();

    /**
     * Not instantiable; this class only holds static helpers.
     */
    private SecureTokenGenerator() {
        throw new AssertionError("SecureTokenGenerator is a utility class and must not be instantiated");
    }

    /**
     * Generates a token meant to be carried by software rather than typed by a person.
     *
     * @return a URL safe token with 256 bits of entropy
     */
    public static String generateMachineToken() {
        final byte[] randomBytes = new byte[MACHINE_TOKEN_ENTROPY_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return URL_SAFE_ENCODER.encodeToString(randomBytes);
    }

    /**
     * Generates a short code a person can read off a web page and type into the extension.
     *
     * <p>The code is grouped into blocks separated by {@code -} purely for legibility; the
     * separator is not part of the secret and is stripped before comparison.</p>
     *
     * @param blockCount     number of blocks
     * @param charsPerBlock  characters in each block
     * @return the formatted code, for example {@code 4F2K-9QX1}
     * @throws IllegalArgumentException when either argument is not positive
     */
    public static String generateHumanReadableCode(final int blockCount, final int charsPerBlock) {
        if (blockCount <= 0 || charsPerBlock <= 0) {
            throw new IllegalArgumentException(
                    "blockCount and charsPerBlock must both be positive, got %d and %d"
                            .formatted(blockCount, charsPerBlock));
        }

        final StringBuilder code = new StringBuilder(blockCount * charsPerBlock + blockCount - 1);
        for (int block = 0; block < blockCount; block++) {
            if (block > 0) {
                code.append('-');
            }
            for (int character = 0; character < charsPerBlock; character++) {
                code.append(HUMAN_READABLE_ALPHABET[
                        SECURE_RANDOM.nextInt(HUMAN_READABLE_ALPHABET.length)]);
            }
        }
        return code.toString();
    }

    /**
     * Puts a code the user typed into the form it is stored in.
     *
     * <p>Separators are dropped and letters upper cased, so {@code 4f2k9qx1} and {@code 4F2K-9QX1}
     * are the same code. Users retype these by hand; being strict about punctuation only produces
     * support requests.</p>
     *
     * @param typedCode the raw value the user submitted
     * @return the normalised code
     */
    public static String normaliseHumanReadableCode(final String typedCode) {
        return typedCode.replace("-", "").replace(" ", "")
                .toUpperCase(java.util.Locale.ROOT);
    }
}
