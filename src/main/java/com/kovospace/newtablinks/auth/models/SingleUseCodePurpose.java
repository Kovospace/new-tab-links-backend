package com.kovospace.newtablinks.auth.models;

/**
 * What a {@link SingleUseCodeEntity} entitles its holder to do.
 *
 * <p>The purpose is part of the lookup, so a code minted for one exchange can never be redeemed
 * at the other endpoint even if it were guessed.</p>
 *
 * @since 0.0.2
 */
public enum SingleUseCodePurpose {

    /**
     * Handed to the website at the end of a provider sign-in, and exchanged by it for a token
     * pair. Machine generated, long, short lived, and never seen by the user.
     */
    WEB_SESSION_HANDOFF,

    /**
     * Shown to a signed-in user on the website and retyped into the browser extension, which
     * exchanges it for a token pair. Short and human readable by necessity.
     */
    EXTENSION_CONNECT
}
