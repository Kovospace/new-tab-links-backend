package com.kovospace.newtablinks.common.models;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One limit an account's plan puts on it - the free plan's, or the Fair Use Policy's for a
 * premium account.
 *
 * <p>The names are part of the API contract: they travel as {@code limit} in a refused request's
 * error body, and the extension and the website branch on them. Rename one and an
 * already-installed client stops recognising the refusal.</p>
 *
 * <p>Profiles and workspaces are <em>slots</em>: the account's first ones, in the order the
 * server first stored them, synchronise, and nothing else does (see {@code SynchronisationSlotReader}).
 * Groups, subgroups and links are caps on their container and refuse growth only. Closed tabs
 * never refuse: the oldest are trimmed. Devices count signed-in extension installations.</p>
 *
 * @since 0.0.18
 */
@Schema(description = "Which plan limit a refused request would have exceeded")
public enum PlanLimit {

    /** Profiles of one account - slots, in the order the server first stored them. */
    PROFILES,

    /** Workspaces (environments) of one profile - slots within a slot-holding profile. */
    WORKSPACES_PER_PROFILE,

    /** Groups of one workspace. */
    GROUPS_PER_WORKSPACE,

    /** Subgroups of one group. */
    SUBGROUPS_PER_GROUP,

    /** Links of one workspace, counted through every group and subgroup of it. */
    LINKS_PER_WORKSPACE,

    /**
     * Closed-tab history entries of one account, across all of its profiles.
     *
     * <p>Never sent in a refusal: the oldest entries beyond it are deleted instead.</p>
     */
    CLOSED_TABS,

    /** Extension installations of one account signed in at the same time. */
    DEVICES
}
