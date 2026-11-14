package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;

public record PassCardRequest(String playerId, String targetPlayerId, CreatureType declaredCreature) {
}
