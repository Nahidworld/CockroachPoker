package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Private peek result for the current receiver")
public record PeekResponse(
        @Schema(description = "6-character room code", example = "ABC123")
        String roomCode,

        @Schema(description = "Player who peeked",
                example = "22222222-2222-2222-2222-222222222222")
        String playerId,

        @Schema(description = "Real creature type of the pending card", example = "FLY")
        CreatureType actualCreature
) {
}
