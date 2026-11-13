package com.cockroach.backend.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lifecycle status of a room/game")
public enum GameStatus {
    WAITING,
    IN_PROGRESS,
    FINISHED
}
