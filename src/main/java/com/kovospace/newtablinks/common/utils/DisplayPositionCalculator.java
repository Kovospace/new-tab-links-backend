package com.kovospace.newtablinks.common.utils;

/**
 * Works out where a newly created item is placed among its siblings.
 *
 * <p>Every ordered collection in this application - environments of a user, groups of an
 * environment, subgroups of a group, links of a group or subgroup - appends new items to the end.
 * The rule is identical everywhere, so it lives here once instead of being repeated in each
 * service.</p>
 *
 * <p>Positions are zero based and dense only by convention: gaps left by deletions are tolerated,
 * because ordering is decided by comparison and never by the absolute value.</p>
 *
 * @since 0.0.1
 */
public final class DisplayPositionCalculator {

    /**
     * Position given to the first item of an empty collection.
     */
    public static final int FIRST_POSITION = 0;

    /**
     * Not instantiable; this class only holds static helpers.
     */
    private DisplayPositionCalculator() {
        throw new AssertionError("DisplayPositionCalculator is a utility class and must not be instantiated");
    }

    /**
     * Returns the position to give an item appended after the current last sibling.
     *
     * @param highestPositionInUse highest position currently taken among the siblings, or
     *                             {@code null} when there are no siblings yet
     * @return {@link #FIRST_POSITION} when there are no siblings, otherwise one past the highest
     */
    public static int calculatePositionForAppendedItem(final Integer highestPositionInUse) {
        if (highestPositionInUse == null) {
            return FIRST_POSITION;
        }
        return highestPositionInUse + 1;
    }
}
