package com.kovospace.newtablinks.common.models;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One of the caps the published Fair Use Policy puts on an account, whatever its plan.
 *
 * <p>The names are part of the API contract: they travel in a refused request's error body, and
 * the extension and the website branch on them. Rename one and
 * an already-installed client stops recognising the refusal.</p>
 *
 * <p>A cap only refuses growth. Data already over a cap - created before the cap existed, or
 * under a higher configured one - is never deleted, and edits, moves within and deletions of it
 * keep working.</p>
 *
 * @since 0.0.16
 */
@Schema(description = "Which Fair Use Policy cap a refused write would have exceeded")
public enum FairUseLimit {

    /** Links in one workspace (environment), counted through every group and subgroup of it. */
    LINKS_PER_WORKSPACE,

    /** Workspaces (environments) of one account, across all of its profiles. */
    WORKSPACES,

    /** Profiles of one account. */
    PROFILES,

    /**
     * Closed-tab history entries of one account, across all of its profiles.
     *
     * <p>Never sent in a refusal: the oldest entries beyond the cap are deleted instead. Kept so
     * the name exists if that ever changes, and so the cap has one place to be configured.</p>
     */
    CLOSED_TAB_HISTORY
}
