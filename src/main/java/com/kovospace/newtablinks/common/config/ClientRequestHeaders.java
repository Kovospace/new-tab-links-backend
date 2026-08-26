package com.kovospace.newtablinks.common.config;

/**
 * Names of the non-standard request headers this service reads from its browser clients.
 *
 * <p>They live here rather than beside the controller that reads them because a custom header is
 * useless on its own: a browser will not send one unless the CORS preflight has allowed it, so
 * every entry has to appear both in a {@code @RequestHeader} binding and in
 * {@link SecurityConfiguration#corsConfigurationSource()}. Keeping the literal in one place is
 * what stops the two from drifting apart - a header declared on a controller but missing from the
 * CORS list fails only in a real browser, never in {@code curl}, which does not enforce CORS.</p>
 *
 * @since 0.0.3
 */
public final class ClientRequestHeaders {

    /**
     * Header a client uses to name the machine it is running on.
     *
     * <p>Optional, and never trusted for anything: a browser cannot read its host's name, so this
     * is whatever the client chose to send. It only labels a row in the user's device list.</p>
     *
     * <p>Sent by the website on the calls that issue tokens, and expected from the extension for
     * the same reason.</p>
     */
    public static final String DEVICE_NAME = "X-Device-Name";

    /**
     * Prevents instantiation of this constant holder.
     */
    private ClientRequestHeaders() {
    }
}
