package com.cockroach.backend.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomCreationRateLimiterTests {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");

    @Test
    void allowsUpToTheLimitPerMinute() {
        RoomCreationRateLimiter limiter = new RoomCreationRateLimiter(3);

        assertTrue(limiter.tryAcquire("1.2.3.4", T0));
        assertTrue(limiter.tryAcquire("1.2.3.4", T0.plusSeconds(10)));
        assertTrue(limiter.tryAcquire("1.2.3.4", T0.plusSeconds(20)));
        assertFalse(limiter.tryAcquire("1.2.3.4", T0.plusSeconds(30)));
    }

    @Test
    void theLimitResetsAfterAMinute() {
        RoomCreationRateLimiter limiter = new RoomCreationRateLimiter(1);
        limiter.tryAcquire("1.2.3.4", T0);

        assertFalse(limiter.tryAcquire("1.2.3.4", T0.plusSeconds(59)));
        assertTrue(limiter.tryAcquire("1.2.3.4", T0.plusSeconds(60)));
    }

    @Test
    void clientsAreCountedSeparately() {
        RoomCreationRateLimiter limiter = new RoomCreationRateLimiter(1);

        assertTrue(limiter.tryAcquire("1.2.3.4", T0));
        assertTrue(limiter.tryAcquire("5.6.7.8", T0));
        assertFalse(limiter.tryAcquire("1.2.3.4", T0));
    }

    @Test
    void zeroTurnsTheLimitOff() {
        RoomCreationRateLimiter limiter = new RoomCreationRateLimiter(0);

        for (int i = 0; i < 1000; i++) {
            assertTrue(limiter.tryAcquire("1.2.3.4", T0));
        }
    }

    @Test
    void pruneForgetsFinishedWindows() {
        RoomCreationRateLimiter limiter = new RoomCreationRateLimiter(5);
        limiter.tryAcquire("a", T0);
        limiter.tryAcquire("b", T0.plusSeconds(30));

        limiter.prune(T0.plusSeconds(61));

        assertEquals(1, limiter.trackedClients());
    }
}
