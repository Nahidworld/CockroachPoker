package com.cockroach.backend.enums;

/**
 * Why the loser lost: one of the two rule-based losing conditions, or leaving a game in progress.
 */
public enum LossReason {
    FOUR_OF_A_KIND,
    EMPTY_HAND,
    LEFT_GAME
}
