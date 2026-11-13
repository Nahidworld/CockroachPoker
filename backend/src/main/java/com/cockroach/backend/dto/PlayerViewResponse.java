package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.PendingCard;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;

import java.util.List;

/**
 * The game as seen by one player: their own hand, plus public information about every player and
 * the card in flight. Only returned to the holder of that player's secret token.
 * {@code peekedCreature} is set only when this player has already seen the pending card.
 */
public record PlayerViewResponse(
        String roomCode,
        GameStatus status,
        String playerId,
        String hostPlayerId,
        String currentPlayerId,
        String loserPlayerId,
        List<CardResponse> hand,
        List<PlayerSummaryResponse> players,
        PendingCardResponse pendingCard,
        CreatureType peekedCreature
) {

    public static PlayerViewResponse from(Room room, Player player) {
        PendingCard pendingCard = room.getGameState().getPendingCard().orElse(null);
        CreatureType peekedCreature = pendingCard != null && pendingCard.hasSeen(player.getId())
                ? pendingCard.getCard().getCreatureType()
                : null;
        return new PlayerViewResponse(
                room.getCode(),
                room.getStatus(),
                player.getId(),
                room.getHostPlayerId(),
                room.getGameState().getCurrentPlayerId(),
                room.getGameState().getLoserPlayerId(),
                player.getHand().stream().map(CardResponse::from).toList(),
                room.getPlayers().stream().map(PlayerSummaryResponse::from).toList(),
                pendingCard == null ? null : PendingCardResponse.from(room),
                peekedCreature);
    }
}
