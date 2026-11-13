package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.model.Card;
import com.cockroach.backend.model.Player;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Public information about a player: hand size only, never hand contents. Collected cards are
 * face-up in the game, so their creature types are public.
 */
@Schema(description = "Public player summary: hand size and face-up collected cards, never hand contents")
public record PlayerSummaryResponse(
        @Schema(description = "Server-assigned player ID",
                example = "11111111-1111-1111-1111-111111111111")
        String id,

        @Schema(description = "Player display name", example = "Alice")
        String name,

        @Schema(description = "Number of cards left in this player's hand", example = "22")
        int handCount,

        @Schema(description = "Face-up collected cards, in collection order",
                example = "[\"FLY\", \"BAT\"]")
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
