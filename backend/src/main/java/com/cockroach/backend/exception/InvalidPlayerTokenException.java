package com.cockroach.backend.exception;

public class InvalidPlayerTokenException extends RuntimeException {

    public InvalidPlayerTokenException(String roomCode) {
        super("Player token is not valid for room: " + roomCode);
    }
}
