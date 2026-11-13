package com.cockroach.backend.dto;

import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Returned only to the player who created or joined a room. Besides the public room information,
 * it carries that player's own id and the secret token needed to read their private hand.
 */
@Schema(description = "Returned to the player who created or joined a room: public room info plus "
        + "that player's ID and secret token")
public record RoomJoinResponse(
        @Schema(description = "6-character room code", example = "ABC123")
        String roomCode,

        @Schema(description = "Player names in join order", example = "[\"Alice\", \"Bob\"]")
        List<String> playerNames,

        @Schema(description = "Current number of players", example = "2")
        int playerCount,

        @Schema(description = "Maximum supported players", example = "6")
        int maxPlayers,

        @Schema(description = "Room status", example = "WAITING")
        GameStatus status,

        @Schema(description = "The caller's server-assigned player ID",
                example = "11111111-1111-1111-1111-111111111111")
        String playerId,

        @Schema(description = "Secret token for GET /api/rooms/{roomCode}/me; keep it private",
                example = "6d1c0f8e-4a1b-4c8e-9f2a-0b7e5d3c1a99")
        String playerToken
) {

    public static RoomJoinResponse from(Room room, Player player) {
        RoomResponse roomResponse = RoomResponse.from(room);
        return new RoomJoinResponse(
                roomResponse.roomCode(),
                roomResponse.playerNames(),
                roomResponse.playerCount(),
                roomResponse.maxPlayers(),
                roomResponse.status(),
                player.getId(),
                player.getToken());
    }
}
