package com.cockroach.backend.dto;

import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.GameState;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;

import java.util.List;

public record RoomResponse(
        String roomCode,
        List<String> playerNames,
        int playerCount,
        int maxPlayers,
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
