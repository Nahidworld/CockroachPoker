package com.cockroach.backend.dto;

import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.enums.LeaveOutcome;

/**
 * Result of a player leaving a room. Built while the room is locked, so it is a consistent snapshot.
 * {@code hostPlayerId} is the host after the player left (null if the room was closed).
 */
public record LeaveRoomResponse(
        String roomCode,
        String playerId,
        String playerName,
        LeaveOutcome outcome,
        GameStatus status,
        String hostPlayerId
) {
}
