package com.cockroach.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for passing the pending card")
public record PassCardRequest(
        @Schema(description = "ID of the current receiver, who must have peeked",
                example = "22222222-2222-2222-2222-222222222222",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String playerId,

        @Schema(description = "ID of the next receiver, who must not have seen the card",
                example = "33333333-3333-3333-3333-333333333333",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String targetPlayerId
) {
}
