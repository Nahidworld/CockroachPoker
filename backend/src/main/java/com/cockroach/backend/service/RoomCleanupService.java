package com.cockroach.backend.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Once a minute, deletes rooms that nobody has used for a while, so finished and abandoned games
 * do not stay in memory until the server restarts.
 */
@Slf4j
@Service
public class RoomCleanupService {

    private final RoomService roomService;
    private final RoomCreationRateLimiter rateLimiter;
    private final Duration idleLimit;
    private final Duration finishedLimit;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "room-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    public RoomCleanupService(
            RoomService roomService,
            RoomCreationRateLimiter rateLimiter,
            @Value("${game.room-idle-minutes:60}") long idleMinutes,
            @Value("${game.finished-room-minutes:10}") long finishedMinutes) {
        this.roomService = roomService;
        this.rateLimiter = rateLimiter;
        this.idleLimit = Duration.ofMinutes(idleMinutes);
        this.finishedLimit = Duration.ofMinutes(finishedMinutes);
    }

    @PostConstruct
    void start() {
        scheduler.scheduleAtFixedRate(this::sweepSafely, 1, 1, TimeUnit.MINUTES);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    /** Removes expired rooms and stale rate-limit entries; returns how many rooms were removed. */
    public int sweep(Instant now) {
        rateLimiter.prune(now);
        return roomService.removeExpiredRooms(now, idleLimit, finishedLimit);
    }

    private void sweepSafely() {
        try {
            int removed = sweep(Instant.now());
            if (removed > 0) {
                log.info("Removed {} expired room(s)", removed);
            }
        } catch (RuntimeException exception) {
            log.warn("Room cleanup failed", exception);
        }
    }
}
