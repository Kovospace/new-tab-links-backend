package com.kovospace.newtablinks.entitlement.models;

/**
 * What the operator's decision about an account's pro standing actually changed.
 *
 * @since 0.0.10
 */
public enum OperatorProDecisionOutcome {

    /** The account was not pro and now holds an operator grant. */
    GRANTED,

    /** The account was already pro, through a payment or a grant; nothing was changed. */
    ALREADY_PRO,

    /** The account's operator grant was taken away. */
    REVOKED,

    /** The account was not pro and stays so; nothing was changed. */
    NOT_PRO;

    /**
     * Tells whether the decision changed anything that deserves a log line.
     *
     * @return {@code true} for {@link #GRANTED} and {@link #REVOKED}
     */
    public boolean changedStanding() {
        return this == GRANTED || this == REVOKED;
    }
}
