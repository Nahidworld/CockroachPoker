package com.cockroach.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * STOMP over WebSocket configuration.
 *
 * <ul>
 *     <li>{@code /ws} — connection endpoint.</li>
 *     <li>{@code /app} — application destination prefix for inbound commands.</li>
 *     <li>{@code /topic} — room-wide public events.</li>
 *     <li>{@code /user/queue} — private events for a single player session.</li>
 * </ul>
 *
 * The MVP has no authentication, so every connection is assigned an anonymous per-session
 * principal. This is only an addressing handle for private queues and is not an identity check.
 * Player identity is still validated against room membership by the game services.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(new AnonymousHandshakeHandler());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    private static final class AnonymousHandshakeHandler extends DefaultHandshakeHandler {

        @Override
        protected Principal determineUser(
                ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
            return new SessionPrincipal(UUID.randomUUID().toString());
        }
    }

    private record SessionPrincipal(String name) implements Principal {

        @Override
        public String getName() {
            return name;
        }
    }
}
