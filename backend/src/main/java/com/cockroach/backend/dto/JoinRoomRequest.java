package com.cockroach.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for joining a room")
public record JoinRoomRequest(
        @Schema(description = "Player display name", example = "Bob",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String playerName
) {
}
