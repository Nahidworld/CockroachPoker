package com.cockroach.backend.exception;

public class ServerBusyException extends RuntimeException {
    public ServerBusyException(String message) {
        super(message);
    }
}
