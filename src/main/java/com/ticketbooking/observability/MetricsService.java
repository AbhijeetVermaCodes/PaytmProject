package com.ticketbooking.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    private final Counter globalConfirmedCounter;
    private final Counter globalInvariantViolationsCounter;
    private final Counter globalIdempotentReplaysCounter;
    private final ConcurrentMap<String, Counter> declinedCounters = new ConcurrentHashMap<>();

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.globalConfirmedCounter = Counter.builder("reservations_confirmed_total")
                .description("Total number of confirmed reservations")
                .register(meterRegistry);

        this.globalInvariantViolationsCounter = Counter.builder("reconciliation_invariant_violations_total")
                .description("Total number of seat reconciliation invariant violations")
                .register(meterRegistry);

        this.globalIdempotentReplaysCounter = Counter.builder("idempotent_replays_total")
                .description("Total number of idempotent replayed responses returned")
                .register(meterRegistry);
    }

    public void recordReservationConfirmed(String showId, int seatCount) {
        globalConfirmedCounter.increment();
        Counter.builder("reservations_confirmed_show_total")
                .tag("show_id", showId)
                .register(meterRegistry)
                .increment();

        Counter.builder("seats_sold_total")
                .tag("show_id", showId)
                .register(meterRegistry)
                .increment(seatCount);
    }

    public void recordReservationDeclined(String showId, String reason) {
        String safeReason = reason != null ? reason.toLowerCase().replace('-', '_') : "unknown";
        declinedCounters.computeIfAbsent(safeReason, r ->
                Counter.builder("reservations_declined_total")
                        .tag("reason", r)
                        .description("Total number of declined reservations categorized by reason")
                        .register(meterRegistry)
        ).increment();
    }

    public void recordIdempotentReplay(String showId) {
        globalIdempotentReplaysCounter.increment();
    }

    public void recordReservationCancelled(String showId, int seatCount) {
        Counter.builder("reservations_cancelled_total")
                .tag("show_id", showId)
                .register(meterRegistry)
                .increment();
    }

    public void recordInvariantViolation(String showId) {
        globalInvariantViolationsCounter.increment();
    }
}
