package com.ticketbooking.controller;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthAndMetricsController {

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    public HealthAndMetricsController(JdbcTemplate jdbcTemplate,
                                      @Autowired MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Liveness probe — checks if application process is alive.
     */
    @GetMapping("/health/live")
    public ResponseEntity<Map<String, Object>> liveness() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    /**
     * Readiness probe — actively checks dependency (PostgreSQL DB reachable) and fails closed (503) if down.
     */
    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());

        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                body.put("status", "UP");
                body.put("database", "CONNECTED");
                return ResponseEntity.ok(body);
            } else {
                body.put("status", "DOWN");
                body.put("database", "UNEXPECTED_RESULT");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
            }
        } catch (Exception e) {
            body.put("status", "DOWN");
            body.put("database", "DISCONNECTED");
            body.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }

    /**
     * Prometheus-formatted metrics scrape summary endpoint.
     */
    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        StringBuilder sb = new StringBuilder();
        sb.append("# HELP reservations_confirmed_total Total confirmed reservations\n");
        sb.append("# TYPE reservations_confirmed_total counter\n");
        sb.append("reservations_confirmed_total ")
                .append((long) meterRegistry.counter("reservations_confirmed_total").count())
                .append("\n\n");

        sb.append("# HELP reconciliation_invariant_violations_total Invariant violations\n");
        sb.append("# TYPE reconciliation_invariant_violations_total counter\n");
        sb.append("reconciliation_invariant_violations_total ")
                .append((long) meterRegistry.counter("reconciliation_invariant_violations_total").count())
                .append("\n\n");

        sb.append("# HELP idempotent_replays_total Replayed idempotent requests\n");
        sb.append("# TYPE idempotent_replays_total counter\n");
        sb.append("idempotent_replays_total ")
                .append((long) meterRegistry.counter("idempotent_replays_total").count())
                .append("\n");

        return sb.toString();
    }
}
