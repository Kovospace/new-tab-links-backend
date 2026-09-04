package com.kovospace.newtablinks.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the rule that decides whether a client may keep the identifier it chose.
 *
 * @since 0.0.5
 */
class ClientAssignedIdentifierPolicyTest {

    @Test
    @DisplayName("keeps the identifier the client chose when nothing else is using it")
    void keepsAFreeClientIdentifier() {

        final UUID chosenByClient = UUID.randomUUID();

        final UUID stored = ClientAssignedIdentifierPolicy.chooseIdentifierForInsert(
                chosenByClient, identifier -> false);

        assertThat(stored).isEqualTo(chosenByClient);
    }

    @Test
    @DisplayName("issues a fresh identifier when the client's is already taken")
    void replacesATakenClientIdentifier() {

        final UUID alreadyTaken = UUID.randomUUID();
        final Set<UUID> inUse = Set.of(alreadyTaken);

        final UUID stored = ClientAssignedIdentifierPolicy.chooseIdentifierForInsert(
                alreadyTaken, inUse::contains);

        assertThat(stored).isNotNull().isNotEqualTo(alreadyTaken);
    }

    @Test
    @DisplayName("issues a fresh identifier when the client supplied none")
    void generatesWhenTheClientSuppliedNothing() {

        final UUID stored = ClientAssignedIdentifierPolicy.chooseIdentifierForInsert(
                null, identifier -> false);

        assertThat(stored).isNotNull();
    }

    @Test
    @DisplayName("does not consult the store when the client supplied no identifier")
    void doesNotAskWhetherNullIsTaken() {

        final boolean[] wasAsked = {false};

        ClientAssignedIdentifierPolicy.chooseIdentifierForInsert(null, identifier -> {
            wasAsked[0] = true;
            return false;
        });

        assertThat(wasAsked[0]).isFalse();
    }
}
