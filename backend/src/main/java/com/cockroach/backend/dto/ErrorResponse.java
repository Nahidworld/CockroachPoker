package com.cockroach.backend.dto;

/**
 * Structured error delivered to a single WebSocket session. It never exposes stack traces or
 * internal exception types.
 */
public record ErrorResponse(String error, String message) {
}
