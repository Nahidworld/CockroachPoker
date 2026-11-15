package com.cockroach.backend.enums;

/**
 * What happened when a player left a room.
 */
public enum LeaveOutcome {
    /** Removed from a room that had not started; the room stays open for the others. */
    LEFT_LOBBY,
    /** The last player left a room that had not started, so the room was deleted. */
    ROOM_CLOSED,
    /** Left a game in progress: the player loses and the game ends. */
    FORFEITED,
    /** The game was already over, so nothing changed. */
    ALREADY_FINISHED
}
