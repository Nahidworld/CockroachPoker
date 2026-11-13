package com.cockroach.backend.dto;

import com.cockroach.backend.model.Player;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A player in a room, including the server-assigned player ID")
public record PlayerResponse(
        @Schema(description = "Server-assigned player ID",
                example = "11111111-1111-1111-1111-111111111111")
        String playerId,

        @Schema(description = "Player display name", example = "Alice")
        String playerName
) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getId(), player.getName());
    }
}
