package com.cockroach.backend.dto;

import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The game as seen by one player: their own hand, plus public information about every player.
 * Only returned to the holder of that player's secret token.
 */
@Schema(description = "Private view for one player: own hand plus public information about every player")
public record PlayerViewResponse(
        @Schema(description = "6-character room code", example = "ABC123")
        String roomCode,

        @Schema(description = "Game status", example = "IN_PROGRESS")
        GameStatus status,

        @Schema(description = "The caller's player ID",
                example = "22222222-2222-2222-2222-222222222222")
        String playerId,

        @Schema(description = "Host player ID",
                example = "11111111-1111-1111-1111-111111111111")
        String hostPlayerId,

        @Schema(description = "Player whose turn it is; null when the game is not in progress",
                example = "11111111-1111-1111-1111-111111111111")
        String currentPlayerId,

        @Schema(description = "The caller's own hand")
        List<CardResponse> hand,

        @Schema(description = "Public summaries of every player, in join order")
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
