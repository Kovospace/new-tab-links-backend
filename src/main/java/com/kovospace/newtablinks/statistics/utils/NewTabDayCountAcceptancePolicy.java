package com.kovospace.newtablinks.statistics.utils;

import com.kovospace.newtablinks.statistics.dtos.NewTabDayCountDto;
import java.time.LocalDate;

/**
 * Decides which reported new-tab counts are believable enough to add to the totals.
 *
 * <p>A refused entry is dropped, not answered with an error: the extension resends whatever the
 * server did not take, so an error would make it resend the same impossible entry forever.</p>
 *
 * <p>The day window is wider than "today" in both directions because the day is the client's
 * local date. One day ahead covers every time zone east of UTC; seven days back covers the
 * unsent days the extension keeps. The count ceiling is far above anything a person does in a
 * day, and is there so one tampered report cannot swamp a day's total.</p>
 *
 * @since 0.0.11
 */
public final class NewTabDayCountAcceptancePolicy {

    /** How many days before the server's date a reported day may be. */
    public static final int MAXIMUM_DAYS_BEFORE_TODAY = 7;

    /** How many days after the server's date a reported day may be. */
    public static final int MAXIMUM_DAYS_AFTER_TODAY = 1;

    /** Smallest count accepted; zero adds nothing and a negative one would subtract. */
    public static final long MINIMUM_COUNT = 1;

    /** Largest count accepted for one day from one report. */
    public static final long MAXIMUM_COUNT = 2000;

    /**
     * Prevents instantiation of this utility class.
     */
    private NewTabDayCountAcceptancePolicy() {
    }

    /**
     * Whether an entry's day and count are both within bounds.
     *
     * @param dayCount the reported entry; its fields are never {@code null} once validated
     * @param today    the server's current date, in UTC
     * @return {@code true} when the entry should be added to the totals
     */
    public static boolean isAccepted(final NewTabDayCountDto dayCount, final LocalDate today) {
        return isDayWithinWindow(dayCount.day(), today) && isCountWithinBounds(dayCount.count());
    }

    /**
     * Whether a day lies within {@code [today - 7, today + 1]}, both ends included.
     *
     * @param day   the reported day
     * @param today the server's current date
     * @return {@code true} when the day is inside the window
     */
    private static boolean isDayWithinWindow(final LocalDate day, final LocalDate today) {
        return !day.isBefore(today.minusDays(MAXIMUM_DAYS_BEFORE_TODAY))
                && !day.isAfter(today.plusDays(MAXIMUM_DAYS_AFTER_TODAY));
    }

    /**
     * Whether a count lies within {@code [1, 2000]}.
     *
     * @param count the reported count
     * @return {@code true} when the count is inside the bounds
     */
    private static boolean isCountWithinBounds(final long count) {
        return count >= MINIMUM_COUNT && count <= MAXIMUM_COUNT;
    }
}
