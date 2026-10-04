package com.kovospace.newtablinks.entitlement.models;

import java.time.Instant;

/**
 * Whether an account is pro at a moment, and where that came from.
 *
 * <p>The source is present only while the account is pro: a lapsed subscription or an ended grant
 * is "not pro", and naming its source alongside would read as though it still counted.</p>
 *
 * @param premium       whether the account's entitlement grants pro at the moment judged
 * @param premiumSource where the granting entitlement came from; {@code null} when not pro
 * @param premiumUntil  when the granting entitlement stops granting - the end of a paid period
 *                      or of a time-limited operator grant; {@code null} when it has no end
 *                      (lifetime) and whenever the account is not pro. Since 0.0.15.
 * @since 0.0.10
 */
public record ProStanding(
        boolean premium,
        EntitlementSource premiumSource,
        Instant premiumUntil) {

    /** The standing of an account with no entitlement, or one that no longer grants. */
    public static final ProStanding NOT_PRO = new ProStanding(false, null, null);

    /**
     * Judges an entitlement at a moment.
     *
     * @param entitlement the account's entitlement row
     * @param moment      the instant to judge at, normally now
     * @return pro with its source and end while the row grants, otherwise {@link #NOT_PRO}
     */
    public static ProStanding of(final EntitlementEntity entitlement, final Instant moment) {
        return entitlement.grantsProAt(moment)
                ? new ProStanding(true, entitlement.getSource(), entitlement.getPaidUntil())
                : NOT_PRO;
    }
}
