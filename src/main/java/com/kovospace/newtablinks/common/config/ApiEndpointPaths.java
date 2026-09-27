package com.kovospace.newtablinks.common.config;

/**
 * Request paths that more than one part of the application has to agree on.
 *
 * <p>Most endpoint paths live only on their controller and need no constant. An entry belongs
 * here when the same literal also has to appear in the security configuration or in a servlet
 * filter, because those are matched by string and a typo produces no error - only an endpoint
 * that is silently unreachable, or silently unguarded.</p>
 *
 * @since 0.0.4
 */
public final class ApiEndpointPaths {

    /**
     * Root path of {@link com.kovospace.newtablinks.auth.controllers.AuthenticationController}.
     */
    public static final String AUTHENTICATION_BASE_PATH = "/api/v1/auth";

    /**
     * Path, relative to {@link #AUTHENTICATION_BASE_PATH}, of the username existence lookup.
     */
    public static final String USERNAME_EXISTENCE_SUBPATH = "/username-existence";

    /**
     * Absolute path of the username existence lookup.
     *
     * <p>Needed in three places that must not drift: the controller mapping, the list of
     * endpoints the resource server lets through without a bearer token, and
     * {@link com.kovospace.newtablinks.common.security.FrontendApiKeyAuthenticationFilter},
     * which is the only thing guarding it.</p>
     */
    public static final String USERNAME_EXISTENCE_PATH =
            AUTHENTICATION_BASE_PATH + USERNAME_EXISTENCE_SUBPATH;

    /**
     * Path, relative to {@link #AUTHENTICATION_BASE_PATH}, of registration.
     */
    public static final String REGISTRATION_SUBPATH = "/register";

    /**
     * Absolute path of registration.
     *
     * <p>A constant for the same reason as the lookup above: registration refuses a taken
     * username with 409, which answers the very question the lookup answers, so the two are
     * guarded together by
     * {@link com.kovospace.newtablinks.common.security.VisitorTokenAuthenticationFilter}.
     * Throttling one and leaving the other open would only move the enumeration by one
     * endpoint.</p>
     */
    public static final String REGISTRATION_PATH =
            AUTHENTICATION_BASE_PATH + REGISTRATION_SUBPATH;

    /**
     * Path, relative to {@link #AUTHENTICATION_BASE_PATH}, that issues a visitor token.
     */
    public static final String VISITOR_TOKEN_SUBPATH = "/visitor-token";

    /**
     * Absolute path that issues a visitor token.
     *
     * <p>Must never be guarded by the filter it feeds: a caller with no token has to be able to
     * get one, and a throttle in front of the only way to satisfy the throttle would lock every
     * visitor out permanently.</p>
     */
    public static final String VISITOR_TOKEN_PATH =
            AUTHENTICATION_BASE_PATH + VISITOR_TOKEN_SUBPATH;

    /**
     * Root path of every endpoint reserved for the operator.
     *
     * <p>Needed as a constant because the security configuration guards the whole subtree by
     * string match: everything below it demands the {@code SCOPE_ADMIN} authority, and the one
     * exception - the sign-in that grants it - is listed separately.</p>
     */
    public static final String ADMINISTRATION_BASE_PATH = "/api/v1/admin";

    /**
     * Everything below {@link #ADMINISTRATION_BASE_PATH}, as a matcher pattern.
     */
    public static final String ADMINISTRATION_PATH_PATTERN = ADMINISTRATION_BASE_PATH + "/**";

    /**
     * Path, relative to {@link #ADMINISTRATION_BASE_PATH}, of the operator sign-in.
     */
    public static final String ADMINISTRATION_SIGN_IN_SUBPATH = "/login";

    /**
     * Absolute path of the operator sign-in.
     *
     * <p>The one admin path reachable without an admin token, because it is how one is obtained.
     * It is not unguarded: {@link com.kovospace.newtablinks.admin.services.AdminSignInService}
     * refuses it outright when no credentials are configured, and locks it after too many
     * failures.</p>
     */
    public static final String ADMINISTRATION_SIGN_IN_PATH =
            ADMINISTRATION_BASE_PATH + ADMINISTRATION_SIGN_IN_SUBPATH;

    /**
     * Absolute path Creem delivers webhooks to - the URL to register in Creem's dashboard, after
     * this service's public base address.
     *
     * <p>A constant because the security configuration has to exempt exactly this path from
     * authentication, and a typo on either side would not fail anything: the endpoint would
     * simply answer every delivery with 401 until Creem stopped trying.</p>
     *
     * @since 0.0.9
     */
    public static final String CREEM_WEBHOOK_PATH = "/api/v1/payments/webhooks/creem";

    /**
     * Prevents instantiation of this constant holder.
     */
    private ApiEndpointPaths() {
    }
}
