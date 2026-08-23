package com.kovospace.newtablinks.auth.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that hashing is stable enough to look tokens up by, and that it hides the token.
 *
 * @since 0.0.2
 */
class TokenHasherTest {

    @Test
    @DisplayName("the same token always hashes to the same value, so lookups work")
    void shouldHashDeterministically() {
        assertThat(TokenHasher.hash("some-token")).isEqualTo(TokenHasher.hash("some-token"));
    }

    @Test
    @DisplayName("different tokens hash differently")
    void shouldProduceDifferentHashesForDifferentTokens() {
        assertThat(TokenHasher.hash("token-a")).isNotEqualTo(TokenHasher.hash("token-b"));
    }

    @Test
    @DisplayName("the hash does not contain the token it was made from")
    void shouldNotLeakTheTokenIntoItsHash() {
        final String token = "a-very-recognisable-token";
        assertThat(TokenHasher.hash(token)).doesNotContain(token);
    }
}
