package com.kovospace.newtablinks.auth.controllers;

import com.kovospace.newtablinks.auth.dtos.VisitorTokenDto;
import com.kovospace.newtablinks.auth.services.VisitorTokenService;
import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands an anonymous visitor the metered pass the guarded endpoints ask for.
 *
 * <p>Its own controller rather than another method on {@link AuthenticationController}: nothing
 * here registers, signs in or ends a session, and the class that does all three is long enough
 * already.</p>
 *
 * @since 0.0.5
 */
@RestController
@RequestMapping(ApiEndpointPaths.AUTHENTICATION_BASE_PATH)
@Tag(name = "Authentication", description = "Registration, sign-in and session lifecycle")
public class VisitorTokenController {

    private final VisitorTokenService visitorTokenService;

    /**
     * Creates the controller.
     *
     * @param visitorTokenService issues the tokens
     */
    public VisitorTokenController(final VisitorTokenService visitorTokenService) {
        this.visitorTokenService = visitorTokenService;
    }

    /**
     * Issues a visitor token.
     *
     * @return the token and the limits it will be judged by
     */
    @PostMapping(ApiEndpointPaths.VISITOR_TOKEN_SUBPATH)
    @Operation(summary = "Obtain a metered pass for the endpoints open to anonymous visitors",
            description = "The website asks for one when a page loads and sends it in the "
                    + "X-Visitor-Token header on the endpoints that disclose whether a username "
                    + "is registered. The response states the limits the token will be judged "
                    + "by, so a client can pace itself rather than discover them by being "
                    + "refused. "
                    + "Open and unlimited on purpose - the caller is anonymous and there is "
                    + "nothing to check - so this bounds the rate of enumeration, not the fact "
                    + "of it.")
    @ApiResponse(responseCode = "200", description = "A freshly issued token")
    public VisitorTokenDto issueVisitorToken() {
        return visitorTokenService.issueToken();
    }
}
