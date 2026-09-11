package com.kovospace.newtablinks.sync.dtos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests the names the kinds of record travel under.
 *
 * <p>This enum is one end of a contract whose other end is written in another repository, so a
 * wire name is not an implementation detail: rename one and the browser extension's operations
 * stop being understood, with a 400 and no explanation of which word was wrong.</p>
 *
 * <p>Worth its own test since {@link SyncEntityKind#CLOSED_TAB} arrived. The five kinds before it
 * were their own Java names lowercased and the mapping could be derived; {@code closedTab} is
 * camel case, so the names are now carried explicitly and can drift from what is written here.
 * </p>
 *
 * @since 0.0.8
 */
class SyncEntityKindTest {

    @Test
    @DisplayName("a closed tab travels as closedTab, in the camel case the extension spells it")
    void closedTabTravelsInCamelCase() {

        assertThat(SyncEntityKind.CLOSED_TAB.getWireName()).isEqualTo("closedTab");
        assertThat(SyncEntityKind.fromWireName("closedTab")).isEqualTo(SyncEntityKind.CLOSED_TAB);
    }

    @Test
    @DisplayName("the five kinds that existed before keep the names they were released under")
    void theOriginalKindsKeepTheirNames() {

        assertThat(SyncEntityKind.PROFILE.getWireName()).isEqualTo("profile");
        assertThat(SyncEntityKind.ENVIRONMENT.getWireName()).isEqualTo("environment");
        assertThat(SyncEntityKind.GROUP.getWireName()).isEqualTo("group");
        assertThat(SyncEntityKind.SUBGROUP.getWireName()).isEqualTo("subgroup");
        assertThat(SyncEntityKind.LINK.getWireName()).isEqualTo("link");
    }

    @ParameterizedTest
    @EnumSource(SyncEntityKind.class)
    @DisplayName("every kind reads back as itself from the name it is written as")
    void everyKindRoundTripsThroughItsWireName(final SyncEntityKind kind) {

        assertThat(SyncEntityKind.fromWireName(kind.getWireName())).isEqualTo(kind);
    }

    @ParameterizedTest
    @EnumSource(SyncEntityKind.class)
    @DisplayName("a kind is recognised whatever case the client wrote it in")
    void everyKindIsRecognisedInAnyCase(final SyncEntityKind kind) {

        assertThat(SyncEntityKind.fromWireName(kind.getWireName().toUpperCase()))
                .isEqualTo(kind);
    }

    @Test
    @DisplayName("a name no kind travels under is refused rather than guessed at")
    void refusesAnUnknownName() {

        assertThatThrownBy(() -> SyncEntityKind.fromWireName("closed_tab"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
