package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;

public record PeekResponse(
        String roomCode,
        String playerId,
        CreatureType actualCreature
) {
}
