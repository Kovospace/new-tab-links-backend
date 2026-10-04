package com.kovospace.newtablinks.common.models;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Why a plan limit refused a request - which of the two published rule sets the account met.
 *
 * <p>Part of the API contract: sent as {@code code} in the 409 error body, and the clients choose
 * their wording by it (upgrade, or the Fair Use Policy page).</p>
 *
 * @since 0.0.18
 */
@Schema(description = "Which rule set refused the request")
public enum PlanLimitRefusalCode {

    /**
     * The account is not premium, and the free plan's limit is below the premium one - upgrading
     * would have let the request through.
     */
    FREE_PLAN_LIMIT_REACHED,

    /**
     * The limit is the Fair Use Policy's - the account is premium, or the free plan has no lower
     * limit of this kind. Upgrading would not help; the policy says how to ask for a raise.
     */
    FAIR_USE_LIMIT_REACHED
}
