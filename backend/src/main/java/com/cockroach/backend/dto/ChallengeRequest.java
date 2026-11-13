package com.cockroach.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for challenging the current declaration")
public record ChallengeRequest(
        @Schema(description = "ID of the current receiver", example = "22222222-2222-2222-2222-222222222222",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String playerId,

        @Schema(description = "true means the declaration is truthful; false calls a bluff", example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean guess
) {
}
