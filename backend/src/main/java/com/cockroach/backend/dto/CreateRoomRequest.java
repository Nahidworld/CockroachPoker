package com.cockroach.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for creating a room")
public record CreateRoomRequest(
        @Schema(description = "Player display name", example = "Alice",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String playerName
) {
}
