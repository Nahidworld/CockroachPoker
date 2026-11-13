package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.enums.GameEventType;
import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.PendingCard;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A public, room-wide event. It intentionally never exposes private hands, the identity or
 * creature type of a pending card that is still in flight, private peek results, or internal
 * game-state objects. The {@code creatureType} field is only populated once a card has been
 * revealed by a challenge resolution.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Public room-wide WebSocket event; inapplicable fields are omitted")
public record GameEventResponse(
        @Schema(description = "Event type", example = "CARD_PASSED")
        GameEventType type,

        @Schema(description = "6-character room code", example = "ABC123")
        String roomCode,

        @Schema(description = "Room status after the action", example = "IN_PROGRESS")
        GameStatus status,

        @Schema(description = "Player who caused the event; meaning depends on the event type",
                example = "11111111-1111-1111-1111-111111111111")
        String actorPlayerId,

        @Schema(description = "Receiver or challenge recipient, when applicable",
                example = "22222222-2222-2222-2222-222222222222")
        String targetPlayerId,

        @Schema(description = "Declared creature type while a card is pending", example = "BAT")
        CreatureType declaredCreature,

        @Schema(description = "Revealed or collected creature type; present only after a challenge",
                example = "FLY")
        CreatureType creatureType,

        @Schema(description = "Player whose turn it is, when the game is still in progress",
                example = "22222222-2222-2222-2222-222222222222")
        String currentPlayerId,

        @Schema(description = "Player who lost, when the game has finished",
                example = "22222222-2222-2222-2222-222222222222")
        String loserPlayerId,

        @Schema(description = "Original sender plus every player who has peeked at the pending card",
                example = "[\"11111111-1111-1111-1111-111111111111\"]")
        List<String> seenPlayerIds,

        @Schema(description = "Human-readable summary; do not parse for game logic",
                example = "A card was passed")
        String message
) {

    public static GameEventResponse playerJoined(Room room, Player player) {
        return new GameEventResponse(
                GameEventType.PLAYER_JOINED,
                room.getCode(),
                room.getStatus(),
                player.getId(),
                null,
                null,
                null,
                room.getGameState().getCurrentPlayerId(),
                null,
                null,
                player.getName() + " joined the room");
    }

    public static GameEventResponse gameStarted(Room room) {
        return new GameEventResponse(
                GameEventType.GAME_STARTED,
                room.getCode(),
                room.getStatus(),
                room.getHostPlayerId(),
                null,
                null,
                null,
                room.getGameState().getCurrentPlayerId(),
                null,
                null,
                "Game started");
    }

    public static GameEventResponse cardPassed(Room room, String fromPlayerId) {
        PendingCard pendingCard = room.getGameState().getPendingCard()
                .orElseThrow(() -> new IllegalStateException("No pending card in room: " + room.getCode()));
        return new GameEventResponse(
                GameEventType.CARD_PASSED,
                room.getCode(),
                room.getStatus(),
                fromPlayerId,
                pendingCard.getCurrentReceiverId(),
                pendingCard.getDeclaredCreature(),
                null,
                room.getGameState().getCurrentPlayerId(),
                null,
                List.copyOf(pendingCard.getSeenPlayerIds()),
                "A card was passed");
    }

    public static GameEventResponse challengeResolved(Room room, ChallengeResponse response) {
        return new GameEventResponse(
                GameEventType.CHALLENGE_RESOLVED,
                room.getCode(),
                room.getStatus(),
                response.challengerId(),
                response.recipientId(),
                response.declaredCreature(),
                response.actualCreature(),
                response.currentPlayerId(),
                response.loserPlayerId(),
                null,
                response.correct() ? "Challenge was correct" : "Challenge was incorrect");
    }

    public static GameEventResponse cardCollected(Room room, String playerId, CreatureType creatureType) {
        Player player = room.getGameState().requirePlayer(playerId);
        return new GameEventResponse(
                GameEventType.CARD_COLLECTED,
                room.getCode(),
                room.getStatus(),
                playerId,
                null,
                null,
                creatureType,
                room.getGameState().getCurrentPlayerId(),
                null,
                null,
                player.getName() + " collected a " + creatureType
                        + " (" + player.countCollectedCards(creatureType) + " total)");
    }

    public static GameEventResponse turnChanged(Room room) {
        return new GameEventResponse(
                GameEventType.TURN_CHANGED,
                room.getCode(),
                room.getStatus(),
                null,
                null,
                null,
                null,
                room.getGameState().getCurrentPlayerId(),
                null,
                null,
                "Turn changed");
    }

    public static GameEventResponse gameFinished(Room room) {
        String loserPlayerId = room.getGameState().getLoserPlayerId();
        String loserName = room.getGameState().requirePlayer(loserPlayerId).getName();
        return new GameEventResponse(
                GameEventType.GAME_FINISHED,
                room.getCode(),
                room.getStatus(),
                loserPlayerId,
                null,
                null,
                null,
                null,
                loserPlayerId,
                null,
                loserName + " lost the game");
    }
}
