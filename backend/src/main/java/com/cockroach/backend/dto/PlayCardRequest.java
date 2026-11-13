package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for playing a card and declaring a creature")
public record PlayCardRequest(
        @Schema(description = "ID of the current player", example = "11111111-1111-1111-1111-111111111111",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String playerId,

        @Schema(description = "ID of a card in the player's hand",
                example = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String cardId,

        @Schema(description = "ID of the receiving player; must not be the sender",
                example = "22222222-2222-2222-2222-222222222222",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String targetPlayerId,

        @Schema(description = "Declared creature type; may be truthful or a bluff", example = "BAT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        CreatureType declaredCreature
) {
}
