package com.kovospace.newtablinks.common.config;

import com.kovospace.newtablinks.common.security.StompAuthenticationInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * The STOMP-over-WebSocket broker used to tell one user's other browsers that their data changed.
 *
 * <p>Authentication happens on the STOMP {@code CONNECT} frame rather than at the HTTP handshake -
 * see {@link StompAuthenticationInterceptor} for why the handshake cannot carry a token. A socket
 * that has not connected successfully can do nothing.</p>
 *
 * <p>Messages go to per-user destinations: the server sends to {@code /user/queue/refresh} for a
 * given user id, and each of that user's browsers receives it on {@code /user/queue/refresh}.
 * Spring resolves the per-session destination from the principal established at connect time.</p>
 *
 * <p><strong>Deployment constraint:</strong> the simple broker configured here keeps subscriptions
 * in the pod's heap, so it only works while the deployment runs a single replica. Scaling out
 * needs sticky sessions or an external broker - and nothing warns you, the messages simply do not
 * arrive at clients connected to the other pod.</p>
 *
 * @since 0.0.1
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    private final String endpointPath;
    private final String userDestinationPrefix;
    private final String[] allowedOriginPatterns;
    private final StompAuthenticationInterceptor stompAuthenticationInterceptor;

    /**
     * Creates the configuration.
     *
     * @param endpointPath                   path the STOMP handshake is served on
     * @param userDestinationPrefix          prefix under which per-user destinations are resolved
     * @param allowedOriginPatterns          origin patterns permitted to open the handshake
     * @param stompAuthenticationInterceptor authenticates the CONNECT frame
     */
    public WebSocketConfiguration(
            @Value("${newtablinks.websocket.endpoint-path}") final String endpointPath,
            @Value("${newtablinks.websocket.user-destination-prefix}") final String userDestinationPrefix,
            @Value("${newtablinks.websocket.allowed-origin-patterns}") final String[] allowedOriginPatterns,
            final StompAuthenticationInterceptor stompAuthenticationInterceptor) {

        this.endpointPath = endpointPath;
        this.userDestinationPrefix = userDestinationPrefix;
        this.allowedOriginPatterns = allowedOriginPatterns.clone();
        this.stompAuthenticationInterceptor = stompAuthenticationInterceptor;
    }

    /**
     * Registers the handshake endpoint the extension connects to.
     *
     * @param registry registry the endpoint is added to
     */
    @Override
    public void registerStompEndpoints(final StompEndpointRegistry registry) {
        registry.addEndpoint(endpointPath).setAllowedOriginPatterns(allowedOriginPatterns);
    }

    /**
     * Configures the in-memory broker and the prefix used for per-user destinations.
     *
     * @param registry registry the broker settings are applied to
     */
    @Override
    public void configureMessageBroker(final MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setUserDestinationPrefix(userDestinationPrefix);
    }

    /**
     * Puts token checking in front of every inbound frame.
     *
     * @param registration inbound channel configuration
     */
    @Override
    public void configureClientInboundChannel(final ChannelRegistration registration) {
        registration.interceptors(stompAuthenticationInterceptor);
    }
}
