package com.kovospace.newtablinks.common.models;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One of the limits the free plan puts on an account that is not premium.
 *
 * <p>The names are part of the API contract: they travel in a refused request's error body, with
 * code {@code FREE_PLAN_LIMIT_REACHED}, and the extension and the website branch on them. Rename
 * one and an already-installed client stops recognising the refusal.</p>
 *
 * <p>A limit only refuses growth. An account that was premium and is no longer keeps whatever it
 * holds above a limit, and can still edit, delete and synchronise it - it just cannot add more.
 * Links have no free-plan limit; the Fair Use Policy's cap per workspace is the only one.</p>
 *
 * @since 0.0.17
 */
@Schema(description = "Which free plan limit a refused write would have exceeded")
public enum FreePlanLimit {

    /** Workspaces (environments) of one account, across all of its profiles. */
    WORKSPACES,

    /** Profiles of one account. */
    PROFILES,

    /**
     * Synchronised installations of one account: devices recorded for an extension installation
     * that reported its own identifier. The website's own sign-ins report none and are not
     * counted.
     */
    DEVICES
}
