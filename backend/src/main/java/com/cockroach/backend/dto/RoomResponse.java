package com.cockroach.backend.dto;

import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.GameState;
import com.cockroach.backend.model.Room;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Public room state; every player is returned with a server-assigned ID. "
        + "Hands and collected cards are never exposed.")
public record RoomResponse(
        @Schema(description = "6-character room code", example = "ABC123")
        String roomCode,

        @Schema(description = "ID of the player who created or joined the room; "
                + "null when reading room state", example = "11111111-1111-1111-1111-111111111111")
        String playerId,

        @Schema(description = "Players in join order, with their IDs")
        List<PlayerResponse> players,

        @Schema(description = "Player names in join order", example = "[\"Alice\", \"Bob\"]")
        List<String> playerNames,

        @Schema(description = "Current number of players", example = "2")
        int playerCount,

        @Schema(description = "Maximum supported players", example = "6")
        int maxPlayers,

        @Schema(description = "Room status", example = "WAITING")
        GameStatus status
) {

    public static RoomResponse from(Room room) {
        return from(room, null);
    }

    public static RoomResponse from(Room room, String playerId) {
        List<PlayerResponse> players = room.getPlayers().stream()
                .map(PlayerResponse::from)
                .toList();
        List<String> playerNames = players.stream()
                .map(PlayerResponse::playerName)
                .toList();

        return new RoomResponse(
                room.getCode(),
                playerId,
                players,
                playerNames,
                playerNames.size(),
                GameState.MAX_PLAYERS,
                room.getStatus()
        );
    }
}
