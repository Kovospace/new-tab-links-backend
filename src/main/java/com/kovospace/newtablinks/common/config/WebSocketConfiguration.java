package com.kovospace.newtablinks.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Enables the STOMP-over-WebSocket message broker used to push refresh signals to a browser.
 *
 * <p><strong>Scope of this class today.</strong> It wires the transport and nothing else. No
 * message is published yet and no destination is subscribed to by application code, because
 * targeting a <em>specific</em> user requires an authenticated principal and this service has no
 * authentication yet. The messaging itself is deliberately left to the assignment that
 * introduces user identity.</p>
 *
 * <p>The intended shape once identity exists: the server sends to a per-user destination via
 * {@code SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/refresh", payload)}, and the
 * extension subscribes to {@code /user/queue/refresh} after connecting to {@code /ws}.</p>
 *
 * <p>Note for deployment: the simple in-memory broker configured here keeps subscriptions in the
 * pod's heap, so it only works correctly while the deployment runs a single replica. Scaling out
 * needs either sticky sessions or an external broker.</p>
 *
 * @since 0.0.1
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    private final String endpointPath;
    private final String userDestinationPrefix;
    private final String[] allowedOriginPatterns;

    /**
     * Creates the configuration from values declared in {@code application.properties}.
     *
     * @param endpointPath          path the STOMP handshake is served on
     * @param userDestinationPrefix prefix under which per-user destinations are resolved
     * @param allowedOriginPatterns origin patterns permitted to open the handshake
     */
    public WebSocketConfiguration(
            @Value("${newtablinks.websocket.endpoint-path}") final String endpointPath,
            @Value("${newtablinks.websocket.user-destination-prefix}") final String userDestinationPrefix,
            @Value("${newtablinks.websocket.allowed-origin-patterns}") final String[] allowedOriginPatterns) {

        this.endpointPath = endpointPath;
        this.userDestinationPrefix = userDestinationPrefix;
        this.allowedOriginPatterns = allowedOriginPatterns.clone();
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
}
