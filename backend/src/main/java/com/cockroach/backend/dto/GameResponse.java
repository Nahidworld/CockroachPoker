package com.cockroach.backend.dto;

import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.Room;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Public game state returned when a game starts; includes every player ID")
public record GameResponse(
        @Schema(description = "6-character room code", example = "ABC123")
        String roomCode,

        @Schema(description = "Game status", example = "IN_PROGRESS")
        GameStatus status,

        @Schema(description = "Players in join order, with their IDs")
        List<PlayerResponse> players,

        @Schema(description = "Player names in join order", example = "[\"Alice\", \"Bob\"]")
        List<String> playerNames,

        @Schema(description = "ID of the player whose turn it is",
                example = "11111111-1111-1111-1111-111111111111")
        String currentPlayerId,

        @Schema(description = "Number of players", example = "2")
        int playerCount
) {

    public static GameResponse from(Room room) {
        List<PlayerResponse> players = room.getPlayers().stream()
                .map(PlayerResponse::from)
                .toList();
        List<String> playerNames = players.stream()
                .map(PlayerResponse::playerName)
                .toList();

        return new GameResponse(
                room.getCode(),
                room.getStatus(),
                players,
                playerNames,
                room.getGameState().getCurrentPlayerId(),
                playerNames.size()
        );
    }
}
