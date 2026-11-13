package com.cockroach.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the REST API. The WebSocket/STOMP contract is documented separately in
 * {@code docs/api/websocket-api.md} because OpenAPI does not describe STOMP destinations.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Cockroach Poker API",
                version = "0.0.1",
                description = """
                        Server-authoritative Cockroach Poker MVP.

                        REST handles room management and also exposes gameplay actions. \
                        WebSocket/STOMP handles real-time gameplay commands and room events.

                        There is no authentication: player IDs are supplied by clients and are not a \
                        security boundary. All state is in memory and is lost on restart. \
                        See docs/api/ for the full contract and known limitations."""
        ),
        servers = @Server(url = "http://localhost:8080", description = "Local development")
)
public class OpenApiConfig {
}
