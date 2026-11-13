package com.cockroach.backend.dto;

import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;

import java.util.List;

/**
 * The game as seen by one player: their own hand, plus public information about every player.
 * Only returned to the holder of that player's secret token.
 */
public record PlayerViewResponse(
        String roomCode,
        GameStatus status,
        String playerId,
        String hostPlayerId,
        String currentPlayerId,
        List<CardResponse> hand,
        List<PlayerSummaryResponse> players
) {

    public static PlayerViewResponse from(Room room, Player player) {
        return new PlayerViewResponse(
                room.getCode(),
                room.getStatus(),
                player.getId(),
                room.getHostPlayerId(),
                room.getGameState().getCurrentPlayerId(),
                player.getHand().stream().map(CardResponse::from).toList(),
                room.getPlayers().stream().map(PlayerSummaryResponse::from).toList());
    }
}
