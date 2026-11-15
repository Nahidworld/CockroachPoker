package com.cockroach.backend.controller;

import com.cockroach.backend.dto.GameEventResponse;
import com.cockroach.backend.enums.GameEventType;
import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import com.cockroach.backend.service.PresenceService;
import com.cockroach.backend.service.RoomService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End to end: a player's socket closing leads to a disconnect notice and, after the grace period, a forfeit. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "game.disconnect-grace-millis=400")
class PresenceWebSocketTests {

    @LocalServerPort
    private int port;

    @Autowired
    private RoomService roomService;

    @Autowired
    private PresenceService presenceService;

    private final List<WebSocketStompClient> clients = new ArrayList<>();

    @AfterEach
    void stopClients() {
        clients.forEach(WebSocketStompClient::stop);
    }

    @Test
    void closingTheTabMidGameAnnouncesItThenForfeits() throws Exception {
        Room room = roomService.createRoom("Alice");
        Player bob = roomService.joinRoom(room.getCode(), "Bob");
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        Player alice = room.getPlayers().get(0);

        StompSession aliceSession = connect(room.getCode(), alice.getToken());
        BlockingQueue<GameEventResponse> events = new LinkedBlockingQueue<>();
        aliceSession.subscribe("/topic/rooms/" + room.getCode(), handler(events));
        StompSession bobSession = connect(room.getCode(), bob.getToken());
        waitUntil(() -> presenceService.isConnected(room.getCode(), bob.getId()));

        bobSession.disconnect();

        GameEventResponse disconnected = nextOfType(events, GameEventType.PLAYER_DISCONNECTED);
        assertEquals(bob.getId(), disconnected.actorPlayerId());
        GameEventResponse finished = nextOfType(events, GameEventType.GAME_FINISHED);
        assertEquals(bob.getId(), finished.loserPlayerId());
        assertEquals("Bob left the game and loses", finished.message());
        assertEquals(GameStatus.FINISHED, room.getStatus());
    }

    @Test
    void reconnectingInTimeKeepsTheGameGoing() throws Exception {
        Room room = roomService.createRoom("Alice");
        Player bob = roomService.joinRoom(room.getCode(), "Bob");
        roomService.startGame(room.getCode(), room.getHostPlayerId());

        StompSession first = connect(room.getCode(), bob.getToken());
        waitUntil(() -> presenceService.isConnected(room.getCode(), bob.getId()));
        first.disconnect();
        waitUntil(() -> !presenceService.isConnected(room.getCode(), bob.getId()));
        connect(room.getCode(), bob.getToken());

        Thread.sleep(1000);
        assertEquals(GameStatus.IN_PROGRESS, room.getStatus());
    }

    @Test
    void connectionsWithoutAValidTokenAreNotTracked() throws Exception {
        Room room = roomService.createRoom("Alice");
        Player alice = room.getPlayers().get(0);

        connect(room.getCode(), "not-a-token");
        Thread.sleep(300);

        assertTrue(!presenceService.isConnected(room.getCode(), alice.getId()));
    }

    private StompSession connect(String roomCode, String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        clients.add(client);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(PresenceService.ROOM_CODE_HEADER, roomCode);
        connectHeaders.add(PresenceService.PLAYER_TOKEN_HEADER, token);
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private static StompFrameHandler handler(BlockingQueue<GameEventResponse> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GameEventResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((GameEventResponse) payload);
            }
        };
    }

    private static GameEventResponse nextOfType(BlockingQueue<GameEventResponse> queue, GameEventType type)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            GameEventResponse event = queue.poll(200, TimeUnit.MILLISECONDS);
            if (event != null && event.type() == type) {
                return event;
            }
        }
        assertNotNull(null, "Did not receive a " + type + " event");
        return null;
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Condition not met in time");
            }
            Thread.sleep(20);
        }
    }
}
