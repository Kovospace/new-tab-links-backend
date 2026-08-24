package com.kovospace.newtablinks.auth.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the shape and the uniqueness of generated secrets.
 *
 * @since 0.0.2
 */
class SecureTokenGeneratorTest {

    @Test
    @DisplayName("a connect code is grouped and drawn only from the unambiguous alphabet")
    void shouldGenerateConnectCodesWithoutConfusableCharacters() {
        for (int attempt = 0; attempt < 200; attempt++) {
            final String code = SecureTokenGenerator.generateHumanReadableCode(2, 4);
            assertThat(code).matches("^[A-Z0-9]{4}-[A-Z0-9]{4}$");
            assertThat(code).doesNotContain("I", "L", "O", "U", "0", "1");
        }
    }

    @Test
    @DisplayName("codes typed with any punctuation or case normalise to the same value")
    void shouldNormaliseTypedCodesToASingleForm() {
        assertThat(SecureTokenGenerator.normaliseHumanReadableCode("4f2k-9qx1")).isEqualTo("4F2K9QX1");
        assertThat(SecureTokenGenerator.normaliseHumanReadableCode("4F2K 9QX1")).isEqualTo("4F2K9QX1");
        assertThat(SecureTokenGenerator.normaliseHumanReadableCode("4F2K9QX1")).isEqualTo("4F2K9QX1");
    }

    @Test
    @DisplayName("machine tokens are URL safe and do not repeat")
    void shouldGenerateDistinctUrlSafeMachineTokens() {
        final Set<String> seen = new HashSet<>();
        for (int attempt = 0; attempt < 500; attempt++) {
            final String token = SecureTokenGenerator.generateMachineToken();
            assertThat(token).matches("^[A-Za-z0-9_-]+$");
            assertThat(seen.add(token)).as("token %s was generated twice", token).isTrue();
        }
    }

    @Test
    @DisplayName("a nonsensical code shape is refused rather than silently producing nothing")
    void shouldRejectNonPositiveCodeDimensions() {
        assertThatThrownBy(() -> SecureTokenGenerator.generateHumanReadableCode(0, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecureTokenGenerator.generateHumanReadableCode(2, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
