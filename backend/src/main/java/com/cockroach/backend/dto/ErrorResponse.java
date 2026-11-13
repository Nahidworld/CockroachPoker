package com.cockroach.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Structured error delivered to a single WebSocket session. It never exposes stack traces or
 * internal exception types.
 */
@Schema(description = "Structured WebSocket error sent to /user/queue/errors")
public record ErrorResponse(
        @Schema(description = "Machine-readable error code", example = "INVALID_GAME_STATE")
        String error,

        @Schema(description = "Human-readable message without stack traces",
                example = "It is not this player's turn in room: ABC123")
        String message
) {
}
