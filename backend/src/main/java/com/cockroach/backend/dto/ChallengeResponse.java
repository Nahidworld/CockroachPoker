package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.enums.GameStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of resolving a challenge")
public record ChallengeResponse(
        @Schema(description = "6-character room code", example = "ABC123")
        String roomCode,

        @Schema(description = "Game status after resolution", example = "IN_PROGRESS")
        GameStatus status,

        @Schema(description = "Player who challenged",
                example = "22222222-2222-2222-2222-222222222222")
        String challengerId,

        @Schema(description = "Whether the challenge was correct", example = "true")
        boolean correct,

        @Schema(description = "Player who collects the card",
                example = "11111111-1111-1111-1111-111111111111")
        String recipientId,

        @Schema(description = "Declared creature type", example = "BAT")
        CreatureType declaredCreature,

        @Schema(description = "Revealed creature type", example = "FLY")
        CreatureType actualCreature,

        @Schema(description = "Player who starts the next round; null when the game finished",
                example = "11111111-1111-1111-1111-111111111111")
        String currentPlayerId,

        @Schema(description = "Player who lost; null while the game continues",
                example = "22222222-2222-2222-2222-222222222222")
        String loserPlayerId
) {
}
