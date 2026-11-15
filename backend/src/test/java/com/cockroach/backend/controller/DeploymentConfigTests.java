package com.cockroach.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Settings needed when the frontend and backend run on different hosts (Vercel + Render):
 * CORS for the configured origins only, and the visitor's address taken from X-Forwarded-For.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "game.allowed-origins=https://cockroach.example.com,https://*.vercel.app",
                "game.room-creation-limit-per-minute=2"
        })
class DeploymentConfigTests {

    private static final String ROOM_BODY = "{\"playerName\":\"Alice\"}";

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void preflightFromAllowedOriginIsAccepted() throws Exception {
        HttpResponse<String> response = preflight("https://cockroach.example.com");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("https://cockroach.example.com");
    }

    @Test
    void preflightFromMatchingOriginPatternIsAccepted() throws Exception {
        HttpResponse<String> response = preflight("https://cockroach-git-main.vercel.app");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("https://cockroach-git-main.vercel.app");
    }

    @Test
    void preflightFromOtherOriginIsRejected() throws Exception {
        HttpResponse<String> response = preflight("https://evil.example.org");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void crossOriginRoomCreationReturnsCorsHeader() throws Exception {
        HttpResponse<String> response = createRoom("203.0.113.50", "https://cockroach.example.com");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("https://cockroach.example.com");
    }

    @Test
    void roomCreationLimitCountsEachForwardedVisitorSeparately() throws Exception {
        assertThat(createRoom("198.51.100.1", null).statusCode()).isEqualTo(201);
        assertThat(createRoom("198.51.100.1", null).statusCode()).isEqualTo(201);
        assertThat(createRoom("198.51.100.1", null).statusCode()).isEqualTo(429);

        // a different visitor behind the same proxy is not blocked
        assertThat(createRoom("198.51.100.2", null).statusCode()).isEqualTo(201);
    }

    @Test
    void webSocketHandshakeFromAllowedOriginSucceeds() throws Exception {
        WebSocket socket = openSocket("https://cockroach.example.com").get(5, TimeUnit.SECONDS);

        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
    }

    @Test
    void webSocketHandshakeFromOtherOriginIsRejected() {
        assertThatThrownBy(() -> openSocket("https://evil.example.org").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(WebSocketHandshakeException.class);
    }

    private CompletableFuture<WebSocket> openSocket(String origin) {
        return http.newWebSocketBuilder()
                .header("Origin", origin)
                .buildAsync(URI.create("ws://localhost:" + port + "/ws"), new WebSocket.Listener() { });
    }

    private HttpResponse<String> preflight(String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri())
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-player-token")
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> createRoom(String forwardedFor, String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri())
                .POST(HttpRequest.BodyPublishers.ofString(ROOM_BODY))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor);
        if (origin != null) {
            request.header("Origin", origin);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri() {
        return URI.create("http://localhost:" + port + "/api/rooms");
    }
}
