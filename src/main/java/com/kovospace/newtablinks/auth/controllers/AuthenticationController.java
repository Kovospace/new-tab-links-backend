package com.kovospace.newtablinks.auth.controllers;

import com.kovospace.newtablinks.auth.dtos.ClientDescriptionDto;
import com.kovospace.newtablinks.auth.dtos.ExtensionConnectCodeDto;
import com.kovospace.newtablinks.auth.dtos.LoginRequestDto;
import com.kovospace.newtablinks.auth.dtos.RefreshRequestDto;
import com.kovospace.newtablinks.auth.dtos.RegistrationAcceptedDto;
import com.kovospace.newtablinks.auth.dtos.RegistrationRequestDto;
import com.kovospace.newtablinks.auth.dtos.SingleUseCodeRedemptionRequestDto;
import com.kovospace.newtablinks.auth.dtos.TokenPairDto;
import com.kovospace.newtablinks.auth.dtos.UsernameExistenceDto;
import com.kovospace.newtablinks.auth.models.SingleUseCodePurpose;
import com.kovospace.newtablinks.auth.services.AuthenticationService;
import com.kovospace.newtablinks.auth.services.RegistrationService;
import com.kovospace.newtablinks.auth.services.SingleUseCodeService;
import com.kovospace.newtablinks.auth.utils.UsernameConstraints;
import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.config.ClientRequestHeaders;
import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.user.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for registering, signing in, and keeping a session alive.
 *
 * <p>Everything here except {@code /extension-connect-codes} is reachable without a token,
 * because these endpoints are how a caller gets one.</p>
 *
 * @since 0.0.2
 */
@RestController
@RequestMapping(ApiEndpointPaths.AUTHENTICATION_BASE_PATH)
@Validated
@Tag(name = "Authentication", description = "Registration, sign-in and session lifecycle")
public class AuthenticationController {

    /**
     * Header a client uses to name the machine it is running on.
     *
     * <p>Aliased from {@link ClientRequestHeaders#DEVICE_NAME} so that the literal exists once:
     * a custom header only reaches this method when the CORS configuration has also allowed it,
     * and the two lists must not drift.</p>
     */
    private static final String DEVICE_NAME_HEADER = ClientRequestHeaders.DEVICE_NAME;
    private static final String INSTALLATION_ID_HEADER = ClientRequestHeaders.INSTALLATION_ID;

    /**
     * Wording returned to every registration attempt, successful or not.
     */
    private static final String UNIFORM_REGISTRATION_MESSAGE =
            "If that address can receive mail, an activation link is on its way.";

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final SingleUseCodeService singleUseCodeService;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final UserService userService;

    /**
     * Creates the controller.
     *
     * @param registrationService       classic registration and activation
     * @param authenticationService     password sign-in, refresh and sign-out
     * @param singleUseCodeService      connect and handoff codes
     * @param authenticatedUserProvider identifies the caller on protected endpoints
     * @param userService               resolves the caller's account
     */
    public AuthenticationController(
            final RegistrationService registrationService,
            final AuthenticationService authenticationService,
            final SingleUseCodeService singleUseCodeService,
            final AuthenticatedUserProvider authenticatedUserProvider,
            final UserService userService) {

        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.singleUseCodeService = singleUseCodeService;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.userService = userService;
    }

    /**
     * Registers an account with a password and mails an activation link.
     *
     * @param registrationRequest the submitted registration
     * @return the uniform acknowledgement
     */
    @PostMapping(ApiEndpointPaths.REGISTRATION_SUBPATH)
    @Operation(summary = "Register an account with a password and mail an activation link",
            description = "Answers identically whether the account was created or the address "
                    + "was already registered, so that this endpoint cannot be used to discover "
                    + "who has an account. A taken username is refused openly, since a username "
                    + "is public by nature. "
                    + "That 409 is why this endpoint is metered by the visitor token: it answers "
                    + "the same question the username lookup answers, and throttling only the "
                    + "lookup would move enumeration here instead of stopping it.")
    @Parameter(name = ClientRequestHeaders.VISITOR_TOKEN, in = ParameterIn.HEADER,
            required = true, schema = @Schema(type = "string"),
            description = "A metered pass from POST /api/v1/auth/visitor-token. Free to obtain; "
                    + "it limits how fast and how often this endpoint may be called, not who "
                    + "may call it.")
    @ApiResponse(responseCode = "202", description = "The attempt was accepted")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "401",
            description = "The visitor token was missing, unknown, expired or spent; obtain a "
                    + "new one and retry",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "That username is already taken",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "429",
            description = "The call came sooner than the visitor token allows; Retry-After says "
                    + "how long to wait",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<RegistrationAcceptedDto> register(
            @Valid @RequestBody final RegistrationRequestDto registrationRequest) {

        return ResponseEntity.accepted().body(RegistrationAcceptedDto.reportingDelivery(
                UNIFORM_REGISTRATION_MESSAGE, registrationService.register(registrationRequest)));
    }

    /**
     * Answers whether a username is already registered.
     *
     * @param username the name the visitor is typing into the registration form
     * @return whether an account already uses that name
     */
    @GetMapping(ApiEndpointPaths.USERNAME_EXISTENCE_SUBPATH)
    @Operation(summary = "Check whether a username is already registered",
            description = "Serves the website's registration form, which reports a taken name "
                    + "while the visitor types instead of only after they submit. "
                    + "It answers the same question registration answers with a 409, and "
                    + "discloses nothing further: a username is public by nature, unlike an "
                    + "email address, which is why registration hides one and reports the other "
                    + "openly. "
                    + "Reserved for the website: every call must carry the "
                    + "X-Frontend-Api-Key header, and a deployment with no key configured "
                    + "refuses the endpoint outright. It is metered on top of that by the "
                    + "visitor token, which is what actually bounds enumeration - the key is "
                    + "public and bounds nothing.")
    @Parameter(name = ClientRequestHeaders.FRONTEND_API_KEY, in = ParameterIn.HEADER,
            required = true, schema = @Schema(type = "string"),
            description = "The shared key issued to the website. Not a secret - it ships in a "
                    + "public JavaScript bundle - it only keeps this endpoint from becoming an "
                    + "open lookup service.")
    @ApiResponse(responseCode = "200", description = "Whether the username is taken")
    @ApiResponse(responseCode = "400",
            description = "The username parameter is missing, or is not a well-formed username",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @Parameter(name = ClientRequestHeaders.VISITOR_TOKEN, in = ParameterIn.HEADER,
            required = true, schema = @Schema(type = "string"),
            description = "A metered pass from POST /api/v1/auth/visitor-token. Free to obtain; "
                    + "it limits how fast and how often this endpoint may be called, not who "
                    + "may call it.")
    @ApiResponse(responseCode = "401",
            description = "The visitor token was missing, unknown, expired or spent; obtain a "
                    + "new one and retry",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "403",
            description = "The frontend API key was missing, wrong, or never configured",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "429",
            description = "The call came sooner than the visitor token allows; Retry-After says "
                    + "how long to wait",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public UsernameExistenceDto checkUsernameExistence(
            @RequestParam
            @NotBlank
            @Size(min = UsernameConstraints.MINIMUM_LENGTH, max = UsernameConstraints.MAXIMUM_LENGTH)
            @Pattern(regexp = UsernameConstraints.ALLOWED_CHARACTERS_PATTERN,
                    message = UsernameConstraints.ALLOWED_CHARACTERS_MESSAGE)
            final String username) {

        return new UsernameExistenceDto(registrationService.isUsernameTaken(username));
    }

    /**
     * Activates an account from the token in an activation link.
     *
     * @param token the token taken from the link
     * @return an empty response
     */
    @GetMapping("/activate")
    @Operation(summary = "Activate an account from the token in an activation link")
    @ApiResponse(responseCode = "204", description = "The account is now active")
    @ApiResponse(responseCode = "400", description = "The token is unknown, spent, or expired",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> activate(@RequestParam @NotBlank final String token) {
        registrationService.activate(token);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sends a fresh activation link to an address awaiting activation.
     *
     * @param email the address to resend to
     * @return the uniform acknowledgement
     */
    @PostMapping("/resend-activation")
    @Operation(summary = "Send a fresh activation link",
            description = "Always answers the same way, whether or not anything was sent. "
                    + "Unlike registration, this leaves emailDelivered unset: only one of its "
                    + "two branches sends anything, so reporting delivery would disclose "
                    + "whether the address is registered and awaiting activation.")
    @ApiResponse(responseCode = "202", description = "The request was accepted")
    public ResponseEntity<RegistrationAcceptedDto> resendActivation(
            @RequestParam @NotBlank @Email final String email) {

        registrationService.resendActivationLink(email);
        return ResponseEntity.accepted()
                .body(RegistrationAcceptedDto.withoutDeliveryReport(UNIFORM_REGISTRATION_MESSAGE));
    }

    /**
     * Signs in with a username or address and a password.
     *
     * @param loginRequest the submitted credentials
     * @param deviceName   optional name of the machine, for the user's device list
     * @param installationId optional identifier of the client installation, which is what
     *                       actually identifies the device
     * @param userAgent    used to name the browser in the user's device list
     * @return the issued token pair
     */
    @PostMapping("/login")
    @Operation(summary = "Sign in with a username or email address and a password")
    @ApiResponse(responseCode = "200", description = "The issued token pair")
    @ApiResponse(responseCode = "401", description = "The credentials were refused",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public TokenPairDto login(
            @Valid @RequestBody final LoginRequestDto loginRequest,
            @RequestHeader(value = DEVICE_NAME_HEADER, required = false) final String deviceName,
            @RequestHeader(value = INSTALLATION_ID_HEADER, required = false)
                    final String installationId,
            @RequestHeader(value = "User-Agent", required = false) final String userAgent) {

        return authenticationService.login(
                loginRequest, ClientDescriptionDto.from(deviceName, userAgent, installationId));
    }

    /**
     * Trades a refresh token for a new pair.
     *
     * @param refreshRequest the presented refresh token
     * @return the issued token pair
     */
    @PostMapping("/refresh")
    @Operation(summary = "Trade a refresh token for a fresh pair",
            description = "The presented token is revoked in the process, so each refresh token "
                    + "works exactly once.")
    @ApiResponse(responseCode = "200", description = "The issued token pair")
    @ApiResponse(responseCode = "401", description = "The refresh token was refused",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public TokenPairDto refresh(@Valid @RequestBody final RefreshRequestDto refreshRequest) {
        return authenticationService.refresh(refreshRequest.refreshToken());
    }

    /**
     * Ends the session behind a refresh token.
     *
     * @param refreshRequest the token to revoke
     * @return an empty response
     */
    @PostMapping("/logout")
    @Operation(summary = "End the session behind a refresh token")
    @ApiResponse(responseCode = "204", description = "The session is ended")
    public ResponseEntity<Void> logout(@Valid @RequestBody final RefreshRequestDto refreshRequest) {
        authenticationService.logout(refreshRequest.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Exchanges the handoff code produced by a provider sign-in for a token pair.
     *
     * @param redemptionRequest the code received on the website's callback page
     * @param deviceName        optional name of the machine, for the user's device list
     * @param installationId    optional identifier of the client installation, which is what
     *                          actually identifies the device
     * @param userAgent         used to name the browser in the user's device list
     * @return the issued token pair
     */
    @PostMapping("/session-handoff")
    @Operation(summary = "Exchange a provider sign-in handoff code for tokens",
            description = "Called by the website when the browser lands on its OAuth callback "
                    + "page carrying a code.")
    @ApiResponse(responseCode = "200", description = "The issued token pair")
    @ApiResponse(responseCode = "400", description = "The code is unknown, spent, or expired",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public TokenPairDto exchangeSessionHandoffCode(
            @Valid @RequestBody final SingleUseCodeRedemptionRequestDto redemptionRequest,
            @RequestHeader(value = DEVICE_NAME_HEADER, required = false) final String deviceName,
            @RequestHeader(value = INSTALLATION_ID_HEADER, required = false)
                    final String installationId,
            @RequestHeader(value = "User-Agent", required = false) final String userAgent) {

        return singleUseCodeService.redeemCode(
                redemptionRequest.code(),
                SingleUseCodePurpose.WEB_SESSION_HANDOFF,
                ClientDescriptionDto.from(deviceName, userAgent, installationId));
    }

    /**
     * Mints a connect code for the signed-in user to type into their browser extension.
     *
     * @return the code to display and its expiry
     */
    @PostMapping("/extension-connect-codes")
    @Operation(summary = "Mint a connect code for the browser extension",
            description = "Called by the website on behalf of a signed-in user. The user reads "
                    + "the code and types it into the extension, which exchanges it for tokens. "
                    + "This is how an account created through a provider, and therefore without "
                    + "a password, signs the extension in.")
    @ApiResponse(responseCode = "201", description = "The minted code")
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<ExtensionConnectCodeDto> mintExtensionConnectCode() {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                singleUseCodeService.mintExtensionConnectCode(
                        userService.getRequiredUserEntity(
                                authenticatedUserProvider.getAuthenticatedUserId())));
    }

    /**
     * Exchanges a connect code typed into the extension for a token pair.
     *
     * @param redemptionRequest the code the user typed
     * @param deviceName        optional name of the machine, for the user's device list
     * @param installationId    optional identifier of the client installation, which is what
     *                          actually identifies the device
     * @param userAgent         used to name the browser in the user's device list
     * @return the issued token pair
     */
    @PostMapping("/extension-connect")
    @Operation(summary = "Exchange a connect code for tokens",
            description = "Called by the browser extension with the code the user typed. "
                    + "Punctuation and letter case are ignored.")
    @ApiResponse(responseCode = "200", description = "The issued token pair")
    @ApiResponse(responseCode = "400", description = "The code is unknown, spent, or expired",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public TokenPairDto exchangeExtensionConnectCode(
            @Valid @RequestBody final SingleUseCodeRedemptionRequestDto redemptionRequest,
            @RequestHeader(value = DEVICE_NAME_HEADER, required = false) final String deviceName,
            @RequestHeader(value = INSTALLATION_ID_HEADER, required = false)
                    final String installationId,
            @RequestHeader(value = "User-Agent", required = false) final String userAgent) {

        return singleUseCodeService.redeemCode(
                redemptionRequest.code(),
                SingleUseCodePurpose.EXTENSION_CONNECT,
                ClientDescriptionDto.from(deviceName, userAgent, installationId));
    }
}
