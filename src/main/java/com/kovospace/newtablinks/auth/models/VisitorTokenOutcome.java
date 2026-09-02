package com.kovospace.newtablinks.auth.models;

/**
 * What happened when a caller presented a visitor token on a guarded endpoint.
 *
 * <p>The reasons are told apart rather than collapsed into one refusal, because the website has
 * to react differently to each: a spent or expired token means "ask for a new one and carry on",
 * while arriving too early means "you are asking faster than the rules allow, slow down". A
 * uniform answer would leave the site guessing, and guessing wrong means either a broken form or
 * a retry loop.</p>
 *
 * <p>Nothing is disclosed by the distinction. The limits themselves are published in the response
 * that issues a token, because the honest client needs them to obey them.</p>
 *
 * @since 0.0.5
 */
public enum VisitorTokenOutcome {

    /**
     * The token was valid and one use has been counted against it.
     */
    ACCEPTED,

    /**
     * No token was presented at all.
     */
    MISSING,

    /**
     * A token was presented, but no such token exists - it was never issued, or it expired long
     * enough ago that its row has already been deleted.
     */
    UNKNOWN,

    /**
     * The token exists but its lifetime has run out.
     */
    EXPIRED,

    /**
     * The token has already made every call it was allowed to make.
     */
    EXHAUSTED,

    /**
     * The call came sooner after the token was issued, or after its previous call, than the
     * configured minimum allows.
     */
    TOO_SOON;

    /**
     * Tells whether the request may proceed.
     *
     * @return {@code true} only for {@link #ACCEPTED}
     */
    public boolean isAccepted() {
        return this == ACCEPTED;
    }

    /**
     * Tells whether a fresh token would fix this refusal.
     *
     * <p>True for every reason except {@link #TOO_SOON}, which a new token would not cure: the
     * caller is simply going too fast, and a replacement token would be refused for its first-use
     * delay anyway.</p>
     *
     * @return {@code true} when the caller should obtain a new token and retry
     */
    public boolean isCuredByANewToken() {
        return this != ACCEPTED && this != TOO_SOON;
    }
}
