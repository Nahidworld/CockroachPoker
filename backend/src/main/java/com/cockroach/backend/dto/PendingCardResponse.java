package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.model.PendingCard;
import com.cockroach.backend.model.Room;

import java.util.List;

public record PendingCardResponse(
        String roomCode,
        String originalSenderId,
        String latestClaimantId,
        CreatureType declaredCreature,
        List<String> seenPlayerIds,
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
