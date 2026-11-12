package com.cockroach.backend.exception;

public class RoomFullException extends RuntimeException {

    public RoomFullException(String roomCode) {
        super("Room is full: " + roomCode);
    }
}
