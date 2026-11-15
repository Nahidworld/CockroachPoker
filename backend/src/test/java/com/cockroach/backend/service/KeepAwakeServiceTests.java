package com.cockroach.backend.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The keep-awake ping: when it is on, what it calls, and how it handles failures. */
class KeepAwakeServiceTests {

    private final AtomicInteger healthHits = new AtomicInteger();
    private HttpServer server;
    private KeepAwakeService service;

    @BeforeEach
    void startFakeBackend() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(KeepAwakeService.HEALTH_PATH, exchange -> {
            healthHits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopEverything() {
        if (service != null) {
            service.stop();
        }
        server.stop(0);
    }

    @Test
    void isOffWithoutAPublicAddress() {
        assertFalse(new KeepAwakeService(true, "", 1000).isEnabled());
        assertFalse(new KeepAwakeService(true, "   ", 1000).isEnabled());
        assertFalse(new KeepAwakeService(true, null, 1000).isEnabled());
    }

    @Test
    void isOffWhenSwitchedOff() {
        assertFalse(new KeepAwakeService(false, baseUrl(), 1000).isEnabled());
    }

    @Test
    void isOffWithANonPositiveInterval() {
        assertFalse(new KeepAwakeService(true, baseUrl(), 0).isEnabled());
    }

    @Test
    void pingCallsTheHealthAddressEvenWithATrailingSlash() {
        service = new KeepAwakeService(true, baseUrl() + "/", 1000);

        assertTrue(service.isEnabled());
        assertEquals(200, service.ping());
        assertEquals(1, healthHits.get());
    }

    @Test
    void pingReportsAnUnreachableServerWithoutThrowing() {
        server.stop(0);
        service = new KeepAwakeService(true, baseUrl(), 1000);

        assertEquals(-1, service.ping());
    }

    @Test
    void startPingsRepeatedlyOnTheInterval() throws Exception {
        service = new KeepAwakeService(true, baseUrl(), 100);

        service.start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (healthHits.get() < 3 && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(healthHits.get() >= 3, "expected at least 3 pings, got " + healthHits.get());
    }

    @Test
    void startDoesNothingWhenOff() throws Exception {
        service = new KeepAwakeService(false, baseUrl(), 50);

        service.start();
        Thread.sleep(300);

        assertEquals(0, healthHits.get());
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }
}
