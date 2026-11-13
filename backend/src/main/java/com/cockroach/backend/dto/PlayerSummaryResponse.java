package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.model.Card;
import com.cockroach.backend.model.Player;

import java.util.List;

/**
 * Public information about a player: hand size only, never hand contents. Collected cards are
 * face-up in the game, so their creature types are public.
 */
public record PlayerSummaryResponse(
        String id,
        String name,
        int handCount,
        List<CreatureType> collectedCards
) {

    public static PlayerSummaryResponse from(Player player) {
        return new PlayerSummaryResponse(
                player.getId(),
                player.getName(),
                player.getHand().size(),
                player.getCollectedCards().stream().map(Card::getCreatureType).toList());
    }
}
