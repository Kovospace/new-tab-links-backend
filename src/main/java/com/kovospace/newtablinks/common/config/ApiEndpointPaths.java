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
     * Prevents instantiation of this constant holder.
     */
    private ApiEndpointPaths() {
    }
}
