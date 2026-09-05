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
     * Header carrying the identifier a client installation minted for itself.
     *
     * <p>Optional, and like {@link #DEVICE_NAME} never trusted for anything: it names a row in
     * the caller's own device list and nothing else. What it is good for is being <em>stable and
     * distinct</em>, which the names are not - the extension builds its device name from
     * {@code navigator.platform}, which is frozen and names the operating system, and Chromium
     * forks impersonate Chrome in the user agent on purpose. Two Chromium browsers on one machine
     * are indistinguishable by name and used to collapse into a single device.</p>
     *
     * <p>Sent by the extension, which has minted one per installation since synchronisation
     * arrived. Not sent by the website: a website is not an installation, and its device rows are
     * still identified by name.</p>
     */
    public static final String INSTALLATION_ID = "X-Installation-Id";

    /**
     * Header the website presents on the endpoints reserved for it.
     *
     * <p>Currently only
     * {@link ApiEndpointPaths#USERNAME_EXISTENCE_PATH} requires it. It is checked by
     * {@link com.kovospace.newtablinks.common.security.FrontendApiKeyAuthenticationFilter}.</p>
     *
     * <p><strong>This is not a secret and must never be treated as one.</strong> It is compiled
     * into a public JavaScript bundle, so anybody who opens the site can read it. Its only job is
     * to stop the endpoint from being a convenient, unattributed lookup service for anyone who
     * finds the API - it raises the cost of casual abuse, and nothing more. Never protect
     * anything with it that a leak would actually damage.</p>
     */
    public static final String FRONTEND_API_KEY = "X-Frontend-Api-Key";

    /**
     * Header carrying the metered pass an anonymous visitor was issued on page load.
     *
     * <p>Required by
     * {@link com.kovospace.newtablinks.common.security.VisitorTokenAuthenticationFilter} on the
     * two endpoints that disclose whether a username is registered:
     * {@link ApiEndpointPaths#USERNAME_EXISTENCE_PATH} and
     * {@link ApiEndpointPaths#REGISTRATION_PATH}.</p>
     *
     * <p>Like {@link #FRONTEND_API_KEY} this is not a secret, and unlike it, it is not meant to
     * be. It is not a credential at all - anyone may ask for one, and asking is free. What it
     * carries is a <em>budget</em>: a token may be spent only so fast and only so often, which
     * is what makes walking a word list through these endpoints slow enough not to be worth
     * doing. Nothing may be authorised by it.</p>
     */
    public static final String VISITOR_TOKEN = "X-Visitor-Token";

    /**
     * Prevents instantiation of this constant holder.
     */
    private ClientRequestHeaders() {
    }
}
