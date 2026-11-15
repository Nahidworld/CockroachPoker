package com.cockroach.backend.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Keeps a free Render service from going to sleep (Render stops it after 15 minutes without
 * incoming requests) by calling its own public {@code /api/health} address every few minutes.
 * The request leaves the server and comes back through Render's proxy, so it counts as traffic.
 *
 * <p>The public address comes from {@code game.keep-awake-url}, which is
 * {@code RENDER_EXTERNAL_URL} (set by Render automatically). Without an address, for example when
 * running locally or in tests, or with {@code game.keep-awake-enabled=false}, nothing is pinged.
 */
@Slf4j
@Service
public class KeepAwakeService {

    static final String HEALTH_PATH = "/api/health";

    private final URI healthUri;
    private final long intervalMillis;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "keep-awake");
        thread.setDaemon(true);
        return thread;
    });

    public KeepAwakeService(
            @Value("${game.keep-awake-enabled:true}") boolean enabled,
            @Value("${game.keep-awake-url:}") String baseUrl,
            @Value("${game.keep-awake-interval-millis:600000}") long intervalMillis) {
        this.healthUri = !enabled || baseUrl == null || baseUrl.isBlank()
                ? null
                : URI.create(baseUrl.strip().replaceAll("/+$", "") + HEALTH_PATH);
        this.intervalMillis = intervalMillis;
    }

    public boolean isEnabled() {
        return healthUri != null && intervalMillis > 0;
    }

    @PostConstruct
    void start() {
        if (!isEnabled()) {
            log.info("Keep-awake ping is off");
            return;
        }
        log.info("Keep-awake ping: {} every {} s", healthUri, intervalMillis / 1000);
        scheduler.scheduleAtFixedRate(this::pingSafely, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    /** Calls the health address once; returns the HTTP status, or -1 if it could not be reached. */
    public int ping() {
        try {
            HttpRequest request = HttpRequest.newBuilder(healthUri)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (Exception exception) {
            log.warn("Keep-awake ping to {} failed: {}", healthUri, exception.getMessage());
            return -1;
        }
    }

    private void pingSafely() {
        int status = ping();
        if (status != 200 && status != -1) {
            log.warn("Keep-awake ping to {} answered {}", healthUri, status);
        }
    }
}
