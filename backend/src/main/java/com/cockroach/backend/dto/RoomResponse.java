package com.cockroach.backend.dto;

import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.GameState;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Public room state; never includes hands, player IDs, or collected cards")
public record RoomResponse(
        @Schema(description = "6-character room code", example = "ABC123")
        String roomCode,

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
        List<String> playerNames = room.getPlayers().stream()
                .map(Player::getName)
                .toList();

        return new RoomResponse(
                room.getCode(),
                playerNames,
                playerNames.size(),
                GameState.MAX_PLAYERS,
                room.getStatus()
        );
    }
}
