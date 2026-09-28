package com.kovospace.newtablinks.statistics.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

/**
 * The primary key of {@link WebsiteVisitorHashEntity}, and the whole of its row.
 *
 * @since 0.0.11
 */
@Embeddable
public class WebsiteVisitorHashKey implements Serializable {

    /** The UTC day the visit was made on. */
    @Column(name = "day", nullable = false, updatable = false)
    private LocalDate visitedOn;

    /** The keyed hash identifying the visitor for that day only. */
    @Column(name = "visitor_hash", nullable = false, updatable = false)
    private byte[] visitorHash;

    /**
     * Required by JPA.
     */
    protected WebsiteVisitorHashKey() {
    }

    /**
     * Compares by day and by the hash's content, as a composite key must.
     *
     * @param other the object to compare with
     * @return {@code true} when both name the same visitor on the same day
     */
    @Override
    public boolean equals(final Object other) {
        return this == other
                || other instanceof WebsiteVisitorHashKey key
                && Objects.equals(visitedOn, key.visitedOn)
                && Arrays.equals(visitorHash, key.visitorHash);
    }

    /**
     * Hashes by day and by the hash's content, consistently with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(visitedOn) + Arrays.hashCode(visitorHash);
    }
}
