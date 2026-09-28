package com.kovospace.newtablinks.statistics.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.kovospace.newtablinks.statistics.dtos.NewTabDayCountDto;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the edges of the window in which a reported new-tab count is believed.
 *
 * @since 0.0.11
 */
class NewTabDayCountAcceptancePolicyTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-09-28");

    @Test
    @DisplayName("accepts days from seven before today to one after, both ends included")
    void shouldAcceptEveryDayInsideTheWindow() {
        assertThat(isAccepted(TODAY.minusDays(7), 1)).isTrue();
        assertThat(isAccepted(TODAY, 1)).isTrue();
        assertThat(isAccepted(TODAY.plusDays(1), 1)).isTrue();
    }

    @Test
    @DisplayName("refuses a day just outside the window on either side")
    void shouldRefuseDaysJustOutsideTheWindow() {
        assertThat(isAccepted(TODAY.minusDays(8), 1)).isFalse();
        assertThat(isAccepted(TODAY.plusDays(2), 1)).isFalse();
    }

    @Test
    @DisplayName("accepts counts from 1 to 2000 and refuses anything else")
    void shouldAcceptOnlyCountsWithinBounds() {
        assertThat(isAccepted(TODAY, 1)).isTrue();
        assertThat(isAccepted(TODAY, 2000)).isTrue();
        assertThat(isAccepted(TODAY, 0)).isFalse();
        assertThat(isAccepted(TODAY, -5)).isFalse();
        assertThat(isAccepted(TODAY, 2001)).isFalse();
    }

    /**
     * Judges one entry against {@link #TODAY}.
     *
     * @param day   the reported day
     * @param count the reported count
     * @return the policy's verdict
     */
    private static boolean isAccepted(final LocalDate day, final long count) {
        return NewTabDayCountAcceptancePolicy.isAccepted(new NewTabDayCountDto(day, count), TODAY);
    }
}
