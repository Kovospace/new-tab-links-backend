package com.kovospace.newtablinks.auth.utils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes an OAuth2 authorization request as JSON for the sign-in cookie, and reads it back.
 *
 * <p>JSON of the named fields, and deliberately not Java serialization, which is what
 * {@link OAuth2AuthorizationRequest} offers: the cookie comes back from a client, and
 * deserialising a client's bytes into arbitrary Java objects is a remote-code-execution hazard
 * however well the bytes are sealed. Here the worst a malformed value can do is fail to parse.</p>
 *
 * <p>Every attribute and additional parameter Spring puts on a login request is a string - the
 * registration id, the PKCE verifier and challenge, the nonce and its hash - so both maps are
 * carried as strings. Anything else is refused when the cookie is written, rather than quietly
 * coming back as a different type on the other pod.</p>
 *
 * @since 0.0.12
 */
public final class AuthorizationRequestCookieCodec {

    private final ObjectMapper objectMapper;

    /**
     * Creates the codec.
     *
     * @param objectMapper the application's JSON mapper
     */
    public AuthorizationRequestCookieCodec(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes a request, stamped with when it was issued.
     *
     * @param authorizationRequest the request Spring is about to send the browser off with
     * @param issuedAt             now, by the clock expiry is later judged against
     * @return the JSON document as UTF-8 bytes
     * @throws IllegalStateException when the request is not an authorization-code request or
     *                               carries a value that is not a string
     */
    public byte[] encode(final OAuth2AuthorizationRequest authorizationRequest, final Instant issuedAt) {
        if (!AuthorizationGrantType.AUTHORIZATION_CODE.equals(authorizationRequest.getGrantType())) {
            throw new IllegalStateException(
                    "Only authorization-code requests are carried in the sign-in cookie");
        }
        return objectMapper.writeValueAsBytes(new AuthorizationRequestCookiePayload(
                authorizationRequest.getAuthorizationUri(),
                authorizationRequest.getClientId(),
                authorizationRequest.getRedirectUri(),
                authorizationRequest.getScopes(),
                authorizationRequest.getState(),
                requireStringValues(authorizationRequest.getAdditionalParameters()),
                requireStringValues(authorizationRequest.getAttributes()),
                authorizationRequest.getAuthorizationRequestUri(),
                issuedAt.getEpochSecond()));
    }

    /**
     * Reads a request back.
     *
     * @param json the bytes {@link #encode} produced, after the cookie was opened
     * @return the request and when it was issued, or empty when the document does not parse
     */
    public Optional<DecodedAuthorizationRequest> decode(final byte[] json) {
        final AuthorizationRequestCookiePayload payload;
        try {
            payload = objectMapper.readValue(json, AuthorizationRequestCookiePayload.class);
        } catch (final JacksonException unreadableDocument) {
            return Optional.empty();
        }
        return Optional.of(new DecodedAuthorizationRequest(
                rebuildAuthorizationRequest(payload),
                Instant.ofEpochSecond(payload.issuedAtEpochSecond())));
    }

    /**
     * Rebuilds the request Spring saved, field for field.
     *
     * @param payload the parsed cookie document
     * @return the authorization request
     */
    private static OAuth2AuthorizationRequest rebuildAuthorizationRequest(
            final AuthorizationRequestCookiePayload payload) {

        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(payload.authorizationUri())
                .clientId(payload.clientId())
                .redirectUri(payload.redirectUri())
                .scopes(payload.scopes())
                .state(payload.state())
                .additionalParameters(Map.copyOf(payload.additionalParameters()))
                .attributes(Map.copyOf(payload.attributes()))
                .authorizationRequestUri(payload.authorizationRequestUri())
                .build();
    }

    /**
     * Narrows a map of objects to the strings it is expected to hold.
     *
     * @param values additional parameters or attributes of a request
     * @return the same entries, typed as strings
     * @throws IllegalStateException when a value is not a string
     */
    private static Map<String, String> requireStringValues(final Map<String, Object> values) {
        final Map<String, String> stringValues = new LinkedHashMap<>();
        values.forEach((name, value) -> {
            if (!(value instanceof String stringValue)) {
                throw new IllegalStateException(
                        "Sign-in cookie can only carry string values, but '%s' is %s"
                                .formatted(name, value == null ? "null" : value.getClass().getName()));
            }
            stringValues.put(name, stringValue);
        });
        return stringValues;
    }

    /**
     * A request read back out of the cookie, with the moment it was issued.
     *
     * @param authorizationRequest the request as Spring saved it
     * @param issuedAt             when it was saved, for the caller to judge expiry by
     */
    public record DecodedAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            Instant issuedAt) {
    }

    /**
     * The document inside the cookie.
     *
     * @param authorizationUri        the provider's authorization endpoint
     * @param clientId                this service's client id at the provider
     * @param redirectUri             where the provider sends the browser back to
     * @param scopes                  the scopes asked for
     * @param state                   the value the callback's {@code state} parameter must match
     * @param additionalParameters    PKCE challenge, nonce hash and similar
     * @param attributes              registration id, PKCE verifier, nonce
     * @param authorizationRequestUri the full address the browser was sent to
     * @param issuedAtEpochSecond     when the request was saved
     */
    public record AuthorizationRequestCookiePayload(
            String authorizationUri,
            String clientId,
            String redirectUri,
            Set<String> scopes,
            String state,
            Map<String, String> additionalParameters,
            Map<String, String> attributes,
            String authorizationRequestUri,
            long issuedAtEpochSecond) {
    }
}
