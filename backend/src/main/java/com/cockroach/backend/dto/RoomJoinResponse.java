package com.cockroach.backend.dto;

import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;

import java.util.List;

/**
 * Returned only to the player who created or joined a room. Besides the public room information,
 * it carries that player's own id and the secret token needed to read their private hand.
 */
public record RoomJoinResponse(
        String roomCode,
        List<String> playerNames,
        int playerCount,
        int maxPlayers,
        GameStatus status,
        String playerId,
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
