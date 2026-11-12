package com.cockroach.backend.exception;

public class NotRoomHostException extends RuntimeException {

    public NotRoomHostException(String roomCode) {
        super("Only the room host can start the game in room: " + roomCode);
    }
}
