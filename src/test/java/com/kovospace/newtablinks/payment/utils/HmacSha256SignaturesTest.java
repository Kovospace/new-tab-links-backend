package com.kovospace.newtablinks.payment.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the signature primitive the webhook's authentication rests on.
 *
 * @since 0.0.9
 */
class HmacSha256SignaturesTest {

    private static final byte[] SECRET = "Jefe".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MESSAGE =
            "what do ya want for nothing?".getBytes(StandardCharsets.UTF_8);

    /** RFC 4231, test case 2. */
    private static final String EXPECTED_SIGNATURE =
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843";

    @Test
    @DisplayName("matches the RFC 4231 reference vector")
    void shouldProduceTheReferenceSignature() {
        assertThat(HmacSha256Signatures.signToHex(SECRET, MESSAGE)).isEqualTo(EXPECTED_SIGNATURE);
    }

    @Test
    @DisplayName("accepts the right signature in either case and with surrounding whitespace")
    void shouldAcceptTheCorrectSignature() {
        assertThat(HmacSha256Signatures.isValidHexSignature(SECRET, MESSAGE, EXPECTED_SIGNATURE))
                .isTrue();
        assertThat(HmacSha256Signatures.isValidHexSignature(
                SECRET, MESSAGE, " " + EXPECTED_SIGNATURE.toUpperCase(Locale.ROOT) + "\n"))
                .isTrue();
    }

    @Test
    @DisplayName("refuses a signature one bit off")
    void shouldRefuseAnAlteredSignature() {
        final String lastCharacterChanged = EXPECTED_SIGNATURE.substring(0, 63) + "2";

        assertThat(HmacSha256Signatures.isValidHexSignature(SECRET, MESSAGE, lastCharacterChanged))
                .isFalse();
    }

    @Test
    @DisplayName("refuses missing, blank, non-hex and wrongly sized signatures without throwing")
    void shouldRefuseUnusableSignatures() {
        assertThat(HmacSha256Signatures.isValidHexSignature(SECRET, MESSAGE, null)).isFalse();
        assertThat(HmacSha256Signatures.isValidHexSignature(SECRET, MESSAGE, "  ")).isFalse();
        assertThat(HmacSha256Signatures.isValidHexSignature(SECRET, MESSAGE, "zz" + "0".repeat(62)))
                .isFalse();
        assertThat(HmacSha256Signatures.isValidHexSignature(
                SECRET, MESSAGE, EXPECTED_SIGNATURE.substring(0, 62))).isFalse();
        assertThat(HmacSha256Signatures.isValidHexSignature(
                SECRET, MESSAGE, "sha256=" + EXPECTED_SIGNATURE)).isFalse();
    }

    @Test
    @DisplayName("refuses the right signature for a different message")
    void shouldRefuseASignatureOfAnotherMessage() {
        final byte[] otherMessage = "what do ya want for nothing!".getBytes(StandardCharsets.UTF_8);

        assertThat(HmacSha256Signatures.isValidHexSignature(SECRET, otherMessage, EXPECTED_SIGNATURE))
                .isFalse();
    }
}
