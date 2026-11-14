package com.cockroach.backend.controller;

import com.cockroach.backend.dto.ChallengeRequest;
import com.cockroach.backend.dto.ErrorResponse;
import com.cockroach.backend.dto.GameEventResponse;
import com.cockroach.backend.dto.PassCardRequest;
import com.cockroach.backend.dto.PeekRequest;
import com.cockroach.backend.dto.PeekResponse;
import com.cockroach.backend.dto.PlayCardRequest;
import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.enums.GameEventType;
import com.cockroach.backend.model.Card;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import com.cockroach.backend.service.RoomService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomWebSocketTests {

    private static final long TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private RoomService roomService;

    private WebSocketStompClient stompClient;
    private StompSession session;

    @BeforeEach
    void connect() throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @AfterEach
    void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    void connectsToTheStompEndpoint() {
        assertTrue(session.isConnected());
    }

    @Test
    void playCommandPublishesPublicCardPassedAndTurnChangedEvents() throws Exception {
        Room room = createStartedGame();
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        String cardId = sender.getHand().get(0).getId();

        BlockingQueue<GameEventResponse> events = subscribeRoomTopic(room);

        send(playDestination(room), new PlayCardRequest(
                sender.getId(), cardId, receiver.getId(), CreatureType.BAT));

        GameEventResponse passed = awaitEvent(events, GameEventType.CARD_PASSED);
        assertEquals(sender.getId(), passed.actorPlayerId());
        assertEquals(receiver.getId(), passed.targetPlayerId());
        assertEquals(CreatureType.BAT, passed.declaredCreature());
        assertNull(passed.creatureType());

        GameEventResponse turn = awaitEvent(events, GameEventType.TURN_CHANGED);
        assertEquals(receiver.getId(), turn.currentPlayerId());
    }

    @Test
    void passCommandPublishesThePassersNewClaim() throws Exception {
        Room room = createStartedGame(3);
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        Player nextReceiver = room.getPlayers().get(2);
        roomService.playCard(room.getCode(), sender.getId(), sender.getHand().get(0).getId(),
                receiver.getId(), CreatureType.BAT);
        roomService.peekCard(room.getCode(), receiver.getId());

        BlockingQueue<GameEventResponse> events = subscribeRoomTopic(room);

        send("/app/rooms/" + room.getCode() + "/pass",
                new PassCardRequest(receiver.getId(), nextReceiver.getId(), CreatureType.SPIDER));

        GameEventResponse passed = awaitEvent(events, GameEventType.CARD_PASSED);
        assertEquals(receiver.getId(), passed.actorPlayerId());
        assertEquals(nextReceiver.getId(), passed.targetPlayerId());
        assertEquals(CreatureType.SPIDER, passed.declaredCreature());
        assertNull(passed.creatureType());
    }

    @Test
    void peekResultIsDeliveredOnlyToTheRequestingPlayer() throws Exception {
        Room room = createStartedGame(3);
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        Card card = sender.getHand().get(0);

        BlockingQueue<GameEventResponse> events = subscribeRoomTopic(room);
        BlockingQueue<PeekResponse> peekResults = subscribe("/user/queue/peek", PeekResponse.class);

        send(playDestination(room), new PlayCardRequest(
                sender.getId(), card.getId(), receiver.getId(), CreatureType.BAT));
        awaitEvent(events, GameEventType.CARD_PASSED);

        send("/app/rooms/" + room.getCode() + "/peek", new PeekRequest(receiver.getId()));

        PeekResponse peek = peekResults.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(peek);
        assertEquals(receiver.getId(), peek.playerId());
        assertEquals(card.getCreatureType(), peek.actualCreature());

        GameEventResponse leaked;
        while ((leaked = events.poll(200, TimeUnit.MILLISECONDS)) != null) {
            assertNull(leaked.creatureType());
        }
    }

    @Test
    void challengeResolutionPublishesCollectedAndTurnChangedEvents() throws Exception {
        Room room = createStartedGame();
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        Card card = sender.getHand().get(0);

        BlockingQueue<GameEventResponse> events = subscribeRoomTopic(room);

        send(playDestination(room), new PlayCardRequest(
                sender.getId(), card.getId(), receiver.getId(), card.getCreatureType()));
        awaitEvent(events, GameEventType.CARD_PASSED);

        send("/app/rooms/" + room.getCode() + "/challenge", new ChallengeRequest(receiver.getId(), true));

        GameEventResponse resolved = awaitEvent(events, GameEventType.CHALLENGE_RESOLVED);
        assertEquals(receiver.getId(), resolved.actorPlayerId());
        assertEquals(sender.getId(), resolved.targetPlayerId());
        assertEquals(card.getCreatureType(), resolved.creatureType());

        GameEventResponse collected = awaitEvent(events, GameEventType.CARD_COLLECTED);
        assertEquals(sender.getId(), collected.actorPlayerId());
        assertEquals(card.getCreatureType(), collected.creatureType());

        GameEventResponse turn = awaitEvent(events, GameEventType.TURN_CHANGED);
        assertEquals(sender.getId(), turn.currentPlayerId());
    }

    @Test
    void invalidCommandIsRejectedAndPublishesNoSuccessEvent() throws Exception {
        Room room = createStartedGame();
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);

        BlockingQueue<GameEventResponse> events = subscribeRoomTopic(room);
        BlockingQueue<ErrorResponse> errors = subscribe("/user/queue/errors", ErrorResponse.class);

        send(playDestination(room), new PlayCardRequest(
                receiver.getId(), receiver.getHand().get(0).getId(), sender.getId(), CreatureType.BAT));

        ErrorResponse error = errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(error);
        assertEquals("INVALID_GAME_STATE", error.error());
        assertNull(events.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void commandFromNonMemberIsRejected() throws Exception {
        Room room = createStartedGame();

        BlockingQueue<ErrorResponse> errors = subscribe("/user/queue/errors", ErrorResponse.class);

        send("/app/rooms/" + room.getCode() + "/peek", new PeekRequest("not-a-member"));

        ErrorResponse error = errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(error);
        assertEquals("INVALID_REQUEST", error.error());
    }

    @Test
    void commandForUnknownRoomIsRejected() throws Exception {
        BlockingQueue<ErrorResponse> errors = subscribe("/user/queue/errors", ErrorResponse.class);

        send("/app/rooms/ZZZZZZ/peek", new PeekRequest("player-id"));

        ErrorResponse error = errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(error);
        assertEquals("ROOM_NOT_FOUND", error.error());
    }

    private void send(String destination, Object payload) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        session.send(headers, payload);
    }

    private BlockingQueue<GameEventResponse> subscribeRoomTopic(Room room) {
        return subscribe("/topic/rooms/" + room.getCode(), GameEventResponse.class);
    }

    private <T> BlockingQueue<T> subscribe(String destination, Class<T> payloadType) {
        BlockingQueue<T> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add(payloadType.cast(payload));
            }
        });
        return queue;
    }

    private GameEventResponse awaitEvent(BlockingQueue<GameEventResponse> events, GameEventType type)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            GameEventResponse event = events.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            if (event == null) {
                break;
            }
            if (event.type() == type) {
                return event;
            }
        }
        return fail("Did not receive a " + type + " event");
    }

    private String playDestination(Room room) {
        return "/app/rooms/" + room.getCode() + "/play";
    }

    private Room createStartedGame() {
        return createStartedGame(2);
    }

    private Room createStartedGame(int playerCount) {
        Room room = roomService.createRoom("Alice");
        for (int i = 2; i <= playerCount; i++) {
            roomService.joinRoom(room.getCode(), "Player " + i);
        }
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        return room;
    }
}
