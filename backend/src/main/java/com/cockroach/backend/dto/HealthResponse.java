package com.cockroach.backend.dto;

/** Answer of {@code GET /api/health}: the server is up. */
public record HealthResponse(String status) {

    public static HealthResponse up() {
        return new HealthResponse("UP");
    }
}
