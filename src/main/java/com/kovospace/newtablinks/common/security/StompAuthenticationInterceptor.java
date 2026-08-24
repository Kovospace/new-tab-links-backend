package com.kovospace.newtablinks.common.security;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Authenticates a websocket client on its STOMP {@code CONNECT} frame.
 *
 * <p>The handshake itself cannot carry a token: a browser's {@code WebSocket} constructor takes a
 * URL and nothing else, so there is no way to set an {@code Authorization} header on it. Putting
 * the token in the query string instead would write it into every access log and proxy trace
 * along the way.</p>
 *
 * <p>STOMP solves this above the transport. {@code CONNECT} is an application level frame with
 * headers of its own, so the token travels in the frame body rather than in the URL, and this
 * interceptor is where it is checked. The HTTP handshake is therefore left open on purpose - it
 * establishes a socket that can do nothing at all until a valid {@code CONNECT} arrives.</p>
 *
 * <p>The principal's name is set to the user's identifier, which is what makes
 * {@code convertAndSendToUser(userId, …)} reach the right client.</p>
 *
 * @since 0.0.3
 */
@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(StompAuthenticationInterceptor.class);

    /**
     * STOMP header the client puts its access token in, mirroring the HTTP one.
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;

    /**
     * Creates the interceptor.
     *
     * @param jwtDecoder validates access tokens, the same decoder the HTTP side uses
     */
    public StompAuthenticationInterceptor(final JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    /**
     * Checks the token on {@code CONNECT} and attaches the resulting principal to the session.
     *
     * <p>Frames other than {@code CONNECT} pass straight through: the principal established here
     * is remembered for the life of the session, so re-checking on every message would only
     * repeat work.</p>
     *
     * @param message message entering the inbound channel
     * @param channel the channel
     * @return the message, unchanged
     * @throws org.springframework.messaging.MessagingException when the token is missing or
     *                                                          invalid, which refuses the connection
     */
    @Override
    public Message<?> preSend(final Message<?> message, final MessageChannel channel) {
        final StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        final Jwt accessToken = decodeAccessToken(accessor);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                accessToken.getSubject(), null, List.of()));

        LOGGER.debug("Websocket connection authenticated for account {}", accessToken.getSubject());
        return message;
    }

    /**
     * Reads and validates the token from the {@code CONNECT} frame.
     *
     * @param accessor accessor over the frame
     * @return the validated token
     * @throws IllegalArgumentException when no usable token is present, refusing the connection
     */
    private Jwt decodeAccessToken(final StompHeaderAccessor accessor) {
        final List<String> authorizationHeaders =
                accessor.getNativeHeader(AUTHORIZATION_HEADER);

        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            LOGGER.debug("Websocket CONNECT refused: no Authorization header");
            throw new IllegalArgumentException("A websocket connection requires an access token");
        }

        final String header = authorizationHeaders.getFirst();
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            LOGGER.debug("Websocket CONNECT refused: Authorization header is not a bearer token");
            throw new IllegalArgumentException("A websocket connection requires a bearer token");
        }

        try {
            return jwtDecoder.decode(header.substring(BEARER_PREFIX.length()));
        } catch (final JwtException invalidToken) {
            LOGGER.debug("Websocket CONNECT refused: {}", invalidToken.getMessage());
            throw new IllegalArgumentException("That access token is not valid", invalidToken);
        }
    }
}
