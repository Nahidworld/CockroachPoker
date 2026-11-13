package com.cockroach.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for peeking at the pending card")
public record PeekRequest(
        @Schema(description = "ID of the current receiver", example = "22222222-2222-2222-2222-222222222222",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String playerId
) {
}
