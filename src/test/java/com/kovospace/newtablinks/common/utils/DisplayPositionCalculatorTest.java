package com.kovospace.newtablinks.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies where {@link DisplayPositionCalculator} places an appended item.
 *
 * @since 0.0.1
 */
class DisplayPositionCalculatorTest {

    @Test
    @DisplayName("first item of an empty collection is placed at position zero")
    void shouldPlaceFirstItemAtPositionZeroWhenThereAreNoSiblings() {
        assertThat(DisplayPositionCalculator.calculatePositionForAppendedItem(null))
                .isEqualTo(DisplayPositionCalculator.FIRST_POSITION);
    }

    @Test
    @DisplayName("an appended item is placed one past the last sibling")
    void shouldPlaceAppendedItemOnePastTheHighestPositionInUse() {
        assertThat(DisplayPositionCalculator.calculatePositionForAppendedItem(4)).isEqualTo(5);
    }

    @Test
    @DisplayName("gaps left by deletions do not shift the appended item")
    void shouldAppendAfterTheHighestPositionEvenWhenPositionsAreNotDense() {
        assertThat(DisplayPositionCalculator.calculatePositionForAppendedItem(42)).isEqualTo(43);
    }
}
