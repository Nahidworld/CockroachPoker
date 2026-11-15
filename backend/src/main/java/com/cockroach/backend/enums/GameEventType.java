package com.cockroach.backend.enums;

/**
 * Types of public real-time events broadcast to a room topic.
 */
public enum GameEventType {
    PLAYER_JOINED,
    PLAYER_LEFT,
    PLAYER_DISCONNECTED,
    PLAYER_RECONNECTED,
    GAME_STARTED,
    CARD_PASSED,
    CHALLENGE_RESOLVED,
    CARD_COLLECTED,
    TURN_CHANGED,
    GAME_FINISHED
}
