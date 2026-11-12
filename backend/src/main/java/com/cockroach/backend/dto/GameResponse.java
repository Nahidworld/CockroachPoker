package com.cockroach.backend.dto;

import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;

import java.util.List;

public record GameResponse(
        String roomCode,
        GameStatus status,
        List<String> playerNames,
        String currentPlayerId,
        int playerCount
) {

    public static GameResponse from(Room room) {
        List<String> playerNames = room.getPlayers().stream()
                .map(Player::getName)
                .toList();

        return new GameResponse(
                room.getCode(),
                room.getStatus(),
                playerNames,
                room.getGameState().getCurrentPlayerId(),
                playerNames.size()
        );
    }
}
