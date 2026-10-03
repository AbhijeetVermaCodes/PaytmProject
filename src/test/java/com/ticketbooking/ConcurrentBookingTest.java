package com.ticketbooking;

import com.ticketbooking.dto.CreateShowRequest;
import com.ticketbooking.dto.ReserveSeatRequest;
import com.ticketbooking.dto.ReserveSeatResponse;
import com.ticketbooking.dto.ShowResponse;
import com.ticketbooking.exception.DomainConflictException;
import com.ticketbooking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ConcurrentBookingTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentBookingTest.class);

    @Autowired
    private BookingService bookingService;

    /**
     * Hot Seat Storm: 50 concurrent users all try to reserve the EXACT SAME seat (A12) at the exact same millisecond.
     * Requirement: Exactly 1 confirmed (201), 49 clean declines (409), 0 server errors (500),
     * and reconciliation invariant holds.
     */
    @Test
    void testHotSeatStampedeSingleWinner() throws InterruptedException {
        int threadCount = 50;
        CreateShowRequest showReq = new CreateShowRequest("hot-seat-storm", List.of("A12"), 25000L, 4);
        ShowResponse show = bookingService.createShow(showReq);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger confirmedCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<String> confirmedReservationIds = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final String userId = "storm-user-" + i;
            final String idempotencyKey = "key-storm-" + i;

            executor.submit(() -> {
                try {
                    startGate.await(); // wait for simultaneous release
                    ReserveSeatRequest req = new ReserveSeatRequest(List.of("A12"), idempotencyKey);
                    ReserveSeatResponse res = bookingService.reserveSeats(show.getId(), req, userId, null);
                    if (res != null && "confirmed".equalsIgnoreCase(res.getStatus())) {
                        confirmedCount.incrementAndGet();
                        confirmedReservationIds.add(res.getReservationId());
                    }
                } catch (DomainConflictException e) {
                    if ("SEAT_ALREADY_TAKEN".equals(e.getReason())) {
                        conflictCount.incrementAndGet();
                    } else {
                        log.warn("Unexpected conflict reason: {}", e.getReason());
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Unexpected error during hot seat storm: ", e);
                    errorCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads must complete within timeout");
        assertEquals(1, confirmedCount.get(), "EXACTLY ONE user must win the hot seat");
        assertEquals(49, conflictCount.get(), "Remaining 49 users must receive clean 409 conflict");
        assertEquals(0, errorCount.get(), "ZERO 500 server errors allowed");
        assertEquals(1, confirmedReservationIds.size());

        // Invariant check on show state
        ShowResponse finalState = bookingService.getShowState(show.getId());
        assertEquals(0, finalState.getAvailableSeats());
        assertEquals(1, finalState.getConfirmedSeats());
        assertEquals(0, finalState.getHeldSeats());
        assertEquals(1, finalState.getTotalSeats());
        assertTrue(finalState.isReconciliationValid(), "Reconciliation invariant must hold to the unit");
    }

    /**
     * Parallel Per-User Limit Contention: A single user fires 10 simultaneous reservations on a show with limit=4.
     * Requirement: User ends with at most 4 seats confirmed; remainder cleanly declined with 409.
     */
    @Test
    void testConcurrentPerUserLimitBurst() throws InterruptedException {
        int threadCount = 10;
        int limit = 4;
        List<String> allSeats = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            allSeats.add("S" + i);
        }

        CreateShowRequest showReq = new CreateShowRequest("limit-burst-show", allSeats, 20000L, limit);
        ShowResponse show = bookingService.createShow(showReq);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        final String userId = "greedy-user-99";
        AtomicInteger confirmedSeats = new AtomicInteger(0);
        AtomicInteger limitDeclines = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String seatToBook = "S" + (i + 1);
            final String idempotencyKey = "greedy-key-" + i;

            executor.submit(() -> {
                try {
                    startGate.await();
                    ReserveSeatRequest req = new ReserveSeatRequest(List.of(seatToBook), idempotencyKey);
                    ReserveSeatResponse res = bookingService.reserveSeats(show.getId(), req, userId, null);
                    if (res != null && "confirmed".equalsIgnoreCase(res.getStatus())) {
                        confirmedSeats.incrementAndGet();
                    }
                } catch (DomainConflictException e) {
                    if ("PER_USER_LIMIT_EXCEEDED".equals(e.getReason())) {
                        limitDeclines.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Unexpected error: ", e);
                    errorCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals(0, errorCount.get());
        assertEquals(limit, confirmedSeats.get(), "User must not exceed per_user_limit under parallel bursts");
        assertEquals(threadCount - limit, limitDeclines.get());

        ShowResponse finalState = bookingService.getShowState(show.getId());
        assertEquals(limit, finalState.getConfirmedSeats());
        assertEquals(20 - limit, finalState.getAvailableSeats());
        assertTrue(finalState.isReconciliationValid());
    }

    /**
     * Multi-Seat Overlapping Request Contention: Multiple threads request intersecting seat sets
     * (e.g. Thread A requests [A1, A2] while Thread B requests [A2, A1] or [A2, A3]).
     * Verifies deterministic ordering prevents database deadlocks.
     */
    @Test
    void testMultiSeatDeadlockAvoidanceUnderContention() throws InterruptedException {
        int threadCount = 20;
        List<String> seats = List.of("M1", "M2", "M3", "M4");
        CreateShowRequest showReq = new CreateShowRequest("multi-seat-show", seats, 10000L, 4);
        ShowResponse show = bookingService.createShow(showReq);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successfulReservations = new AtomicInteger(0);
        AtomicInteger declinedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<List<String>> seatCombinations = List.of(
                List.of("M1", "M2"),
                List.of("M2", "M1"), // reverse order in request
                List.of("M2", "M3"),
                List.of("M3", "M4"),
                List.of("M4", "M1")
        );

        for (int i = 0; i < threadCount; i++) {
            final String userId = "multi-user-" + i;
            final String idempotencyKey = "multi-key-" + i;
            final List<String> requestedSeats = seatCombinations.get(i % seatCombinations.size());

            executor.submit(() -> {
                try {
                    startGate.await();
                    ReserveSeatRequest req = new ReserveSeatRequest(requestedSeats, idempotencyKey);
                    ReserveSeatResponse res = bookingService.reserveSeats(show.getId(), req, userId, null);
                    if (res != null) {
                        successfulReservations.incrementAndGet();
                    }
                } catch (DomainConflictException e) {
                    declinedCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Deadlock or failure detected: ", e);
                    errorCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals(0, errorCount.get(), "Must not encounter database deadlocks or 500 errors");
        assertTrue(successfulReservations.get() >= 1, "At least one multi-seat booking must succeed");

        ShowResponse finalState = bookingService.getShowState(show.getId());
        assertTrue(finalState.isReconciliationValid(), "Invariant must strictly hold");
    }
}
