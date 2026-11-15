package com.cockroach.backend.service;

import com.cockroach.backend.dto.ChallengeResponse;
import com.cockroach.backend.dto.ErrorResponse;
import com.cockroach.backend.dto.GameEventResponse;
import com.cockroach.backend.dto.LeaveRoomResponse;
import com.cockroach.backend.dto.PeekResponse;
import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes real-time WebSocket events. Public events go to the room topic and private results
 * go to a single session's user queue. It is only ever called after a state change succeeded.
 */
@Service
@RequiredArgsConstructor
public class RoomEventPublisher {

    public static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";
    public static final String PEEK_DESTINATION = "/queue/peek";
    public static final String ERROR_DESTINATION = "/queue/errors";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishPlayerJoined(Room room, Player player) {
        publish(GameEventResponse.playerJoined(room, player));
    }

    /**
     * Tells the room that a player left: a lobby update, or the end of the game when the player
     * forfeited. Nothing is sent when the room was closed (nobody is left) or the game was already over.
     */
    public void publishPlayerLeft(LeaveRoomResponse leave, Room room) {
        switch (leave.outcome()) {
            case LEFT_LOBBY -> publish(GameEventResponse.playerLeft(leave));
            case FORFEITED -> publishGameFinished(room);
            default -> { }
        }
    }

    public void publishPlayerDisconnected(
            String roomCode, GameStatus status, String playerId, String playerName, long graceSeconds) {
        publish(GameEventResponse.playerDisconnected(roomCode, status, playerId, playerName, graceSeconds));
    }

    public void publishPlayerReconnected(String roomCode, GameStatus status, String playerId, String playerName) {
        publish(GameEventResponse.playerReconnected(roomCode, status, playerId, playerName));
    }

    public void publishGameStarted(Room room) {
        publish(GameEventResponse.gameStarted(room));
    }

    public void publishCardPassed(Room room, String fromPlayerId) {
        publish(GameEventResponse.cardPassed(room, fromPlayerId));
    }

    public void publishChallengeResolved(Room room, ChallengeResponse response) {
        publish(GameEventResponse.challengeResolved(room, response));
    }

    public void publishCardCollected(Room room, String playerId, CreatureType creatureType) {
        publish(GameEventResponse.cardCollected(room, playerId, creatureType));
    }

    public void publishTurnChanged(Room room) {
        publish(GameEventResponse.turnChanged(room));
    }

    public void publishGameFinished(Room room) {
        publish(GameEventResponse.gameFinished(room));
    }

    public void sendPeekResult(String user, PeekResponse response) {
        messagingTemplate.convertAndSendToUser(user, PEEK_DESTINATION, response);
    }

    public void sendError(String user, ErrorResponse error) {
        messagingTemplate.convertAndSendToUser(user, ERROR_DESTINATION, error);
    }

    private void publish(GameEventResponse event) {
        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + event.roomCode(), event);
    }
}
