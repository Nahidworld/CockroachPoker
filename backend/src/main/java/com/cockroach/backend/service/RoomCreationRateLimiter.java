package com.cockroach.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits how many rooms one client (by IP address) can create per minute, so a single client
 * cannot fill the server's memory with rooms. A limit of 0 or less turns the check off.
 */
@Service
public class RoomCreationRateLimiter {

    static final Duration WINDOW = Duration.ofMinutes(1);

    private final int limitPerWindow;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RoomCreationRateLimiter(@Value("${game.room-creation-limit-per-minute:10}") int limitPerWindow) {
        this.limitPerWindow = limitPerWindow;
    }

    /** Counts one room creation for this client; false if the client is over the limit. */
    public boolean tryAcquire(String clientKey, Instant now) {
        if (limitPerWindow <= 0) {
            return true;
        }
        String key = clientKey == null ? "unknown" : clientKey;
        Window window = windows.compute(key, (k, current) ->
                current == null || !now.isBefore(current.start().plus(WINDOW))
                        ? new Window(now, 1)
                        : new Window(current.start(), current.count() + 1));
        return window.count() <= limitPerWindow;
    }

    /** Forgets clients whose window has ended, so the map does not grow forever. */
    public void prune(Instant now) {
        windows.values().removeIf(window -> !now.isBefore(window.start().plus(WINDOW)));
    }

    int trackedClients() {
        return windows.size();
    }

    private record Window(Instant start, int count) {
    }
}
