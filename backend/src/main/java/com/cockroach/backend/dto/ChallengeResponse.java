package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.enums.GameStatus;

public record ChallengeResponse(
        String roomCode,
        GameStatus status,
        String challengerId,
        boolean correct,
        String recipientId,
        CreatureType declaredCreature,
        CreatureType actualCreature,
        String currentPlayerId,
        String loserPlayerId
) {
}
