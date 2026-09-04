package com.kovospace.newtablinks.sync.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Why one operation of a push was not applied while the rest of the push was.
 *
 * <p>Deliberately coarse. A reason is something the client acts on - re-sending the parent, or
 * fixing a payload it built wrongly - and a finer taxonomy would only invite a client to branch
 * on distinctions this server does not promise to keep.</p>
 *
 * @since 0.0.6
 */
@Schema(description = "Why a single operation of a push was not applied")
public enum SyncRejectionReason {

    /**
     * The record this operation hangs off does not exist for this account.
     *
     * <p>Also what a client sees when it names a parent belonging to somebody else, because the
     * two are deliberately indistinguishable.</p>
     */
    PARENT_NOT_FOUND,

    /**
     * The operation left out something its kind cannot be applied without.
     */
    MISSING_REQUIRED_VALUE
}
