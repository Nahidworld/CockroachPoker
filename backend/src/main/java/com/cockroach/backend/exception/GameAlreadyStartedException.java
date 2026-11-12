package com.cockroach.backend.exception;

public class GameAlreadyStartedException extends RuntimeException {

    public GameAlreadyStartedException(String roomCode) {
        super("Game has already started in room: " + roomCode);
    }
}
