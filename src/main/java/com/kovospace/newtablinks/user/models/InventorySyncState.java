package com.kovospace.newtablinks.user.models;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whether one profile or workspace an installation holds synchronises, as the installation
 * itself decided and reported it.
 *
 * <p>Part of the API contract of the inventory report; the backend stores it as sent and never
 * re-derives it.</p>
 *
 * @since 0.0.18
 */
@Schema(description = "Whether a profile or workspace on an installation synchronises")
public enum InventorySyncState {

    /** It holds a synchronisation slot and is synchronised with the account. */
    SYNCHRONISED,

    /** It stays on the installation only, because the free plan's slots are taken. */
    LOCAL_ONLY_FREE_LIMIT,

    /** It stays on the installation only, because it would exceed a Fair Use cap. */
    LOCAL_ONLY_FAIR_USE,

    /** Untouched example data from the start screen, which never synchronises. */
    EXAMPLE
}
