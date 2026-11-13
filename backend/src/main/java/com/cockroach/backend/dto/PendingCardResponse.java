package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.model.PendingCard;
import com.cockroach.backend.model.Room;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Public pending-card state; never reveals the card's real creature type or ID")
public record PendingCardResponse(
        @Schema(description = "6-character room code", example = "ABC123")
        String roomCode,

        @Schema(description = "Player who first played the card",
                example = "11111111-1111-1111-1111-111111111111")
        String originalSenderId,

        @Schema(description = "Player who last declared or passed the card",
                example = "11111111-1111-1111-1111-111111111111")
        String latestClaimantId,

        @Schema(description = "Declared creature type; may be truthful or a bluff", example = "BAT")
        CreatureType declaredCreature,

        @Schema(description = "Original sender plus every player who has peeked",
                example = "[\"11111111-1111-1111-1111-111111111111\"]")
        List<String> seenPlayerIds,

        @Schema(description = "Player who must peek, pass, or challenge",
                example = "22222222-2222-2222-2222-222222222222")
        String currentReceiverId
) {

    public static PendingCardResponse from(Room room) {
        PendingCard pendingCard = room.getGameState().getPendingCard()
                .orElseThrow(() -> new IllegalStateException("No pending card in room: " + room.getCode()));

        return new PendingCardResponse(
                room.getCode(),
                pendingCard.getOriginalSenderId(),
                pendingCard.getLatestClaimantId(),
                pendingCard.getDeclaredCreature(),
                List.copyOf(pendingCard.getSeenPlayerIds()),
                pendingCard.getCurrentReceiverId()
        );
    }
}
