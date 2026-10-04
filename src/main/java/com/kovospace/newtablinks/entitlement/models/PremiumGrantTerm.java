package com.kovospace.newtablinks.entitlement.models;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * How long pro given by the operator lasts.
 *
 * <p>Sent by the admin user endpoints as {@code premiumGrantTerm}, and stored only through what it
 * produces: the grant's {@code paid_until}, which the migrated schema allows on a grant and which
 * {@link EntitlementEntity#grantsProAt(Instant)} already honours.</p>
 *
 * @since 0.0.15
 */
public enum PremiumGrantTerm {

    /** Pro for one calendar year from the moment of granting, counted in UTC. */
    ONE_YEAR,

    /** Pro without an end, until the operator takes it back. */
    LIFETIME;

    /** The term used when the operator grants pro without naming one. */
    public static final PremiumGrantTerm DEFAULT_TERM = LIFETIME;

    /**
     * Tells when a grant of this term, made at the given moment, stops granting pro.
     *
     * @param grantedAt the moment the grant is made, normally now
     * @return the end of the grant; {@code null} for {@link #LIFETIME}, which has none
     */
    public Instant grantedUntilWhenGrantedAt(final Instant grantedAt) {
        return switch (this) {
            case ONE_YEAR -> grantedAt.atZone(ZoneOffset.UTC).plusYears(1).toInstant();
            case LIFETIME -> null;
        };
    }
}
