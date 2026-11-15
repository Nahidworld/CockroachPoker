package com.cockroach.backend.controller;

import com.cockroach.backend.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cheap endpoint for hosting health checks and the keep-awake ping. Touches no game state. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public HealthResponse health() {
        return HealthResponse.up();
    }
}
