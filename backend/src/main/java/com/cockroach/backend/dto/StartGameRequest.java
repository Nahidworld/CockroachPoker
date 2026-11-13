package com.cockroach.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for starting a game")
public record StartGameRequest(
        @Schema(description = "ID of the host player", example = "11111111-1111-1111-1111-111111111111",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String playerId
) {
}
