package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;

public record PlayCardRequest(
        String playerId,
        String cardId,
        String targetPlayerId,
        CreatureType declaredCreature
) {
}
