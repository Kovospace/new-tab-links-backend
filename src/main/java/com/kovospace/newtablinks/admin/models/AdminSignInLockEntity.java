package com.kovospace.newtablinks.admin.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The operator sign-in lockout, as every replica sees it: one row, never more.
 *
 * <p>Written only by the native statements in {@code AdminSignInLockRepository}, which change it
 * atomically in the database; this class exists so that {@code ddl-auto=validate} checks the
 * table against {@code V13}, and so the row can be read back for logging. Like the statistics
 * entities it has no audit columns - it is a counter, not a record of anything.</p>
 *
 * @since 0.0.13
 */
@Entity
@Table(name = "admin_sign_in_lock")
public class AdminSignInLockEntity {

    /** The only identifier the table admits. */
    public static final short SINGLE_ROW_ID = 1;

    /** Always {@link #SINGLE_ROW_ID}; the table's CHECK constraint refuses anything else. */
    @Id
    @Column(name = "id", nullable = false)
    private short id;

    /** Failures since the last success or the last expiry of a lock. */
    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    /** When sign-in is accepted again, or {@code null} when it is not locked. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    /**
     * Required by JPA.
     */
    protected AdminSignInLockEntity() {
    }

    /**
     * Returns the failures counted so far.
     *
     * @return zero or more
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * Returns when the current lock lifts.
     *
     * @return the moment, or {@code null} when sign-in is not locked
     */
    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
