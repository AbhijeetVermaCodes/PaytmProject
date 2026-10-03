package com.ticketbooking;

import com.ticketbooking.dto.*;
import com.ticketbooking.exception.DomainConflictException;
import com.ticketbooking.exception.ForbiddenException;
import com.ticketbooking.security.UserContext;
import com.ticketbooking.service.BookingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class BookingServiceIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        UserContext.clear();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void testCreateShowAndCheckState() {
        CreateShowRequest request = new CreateShowRequest("rock-concert", List.of("A1", "A2", "A3", "B1", "B2"), 25000L, 4);
        ShowResponse show = bookingService.createShow(request);

        assertNotNull(show.getId());
        assertEquals("rock-concert", show.getName());
        assertEquals(5, show.getTotalSeats());
        assertEquals(5, show.getAvailableSeats());
        assertEquals(0, show.getHeldSeats());
        assertEquals(0, show.getConfirmedSeats());
        assertTrue(show.isReconciliationValid());
    }

    @Test
    void testReserveSeatSuccessAndIdempotentReplay() {
        CreateShowRequest showReq = new CreateShowRequest("movie-night", List.of("A1", "A2", "A3"), 15000L, 4);
        ShowResponse show = bookingService.createShow(showReq);

        UserContext.setUser("user-alpha", false);
        ReserveSeatRequest reserveReq = new ReserveSeatRequest(List.of("A1"), "idem-key-101");

        ReserveSeatResponse res1 = bookingService.reserveSeats(show.getId(), reserveReq, "user-alpha", null);
        assertNotNull(res1.getReservationId());
        assertEquals("confirmed", res1.getStatus());
        assertEquals(15000L, res1.getAmountPaise());
        assertEquals(List.of("A1"), res1.getSeats());

        // Idempotent retry with exact same key and payload
        ReserveSeatResponse res2 = bookingService.reserveSeats(show.getId(), reserveReq, "user-alpha", null);
        assertEquals(res1.getReservationId(), res2.getReservationId());
        assertEquals(res1.getAmountPaise(), res2.getAmountPaise());

        // Reusing key with different seats must fail with 409
        ReserveSeatRequest diffPayloadReq = new ReserveSeatRequest(List.of("A2"), "idem-key-101");
        DomainConflictException ex = assertThrows(DomainConflictException.class, () ->
                bookingService.reserveSeats(show.getId(), diffPayloadReq, "user-alpha", null));
        assertEquals("IDEMPOTENCY_PAYLOAD_MISMATCH", ex.getReason());
    }

    @Test
    void testPerUserLimitEnforcement() {
        CreateShowRequest showReq = new CreateShowRequest("standup-comedy", List.of("A1", "A2", "A3", "A4", "A5"), 10000L, 2);
        ShowResponse show = bookingService.createShow(showReq);

        UserContext.setUser("user-beta", false);
        ReserveSeatRequest req1 = new ReserveSeatRequest(List.of("A1", "A2"), "idem-user-beta-1");
        ReserveSeatResponse res1 = bookingService.reserveSeats(show.getId(), req1, "user-beta", null);
        assertNotNull(res1.getReservationId());

        // Attempting to reserve 3rd seat when limit is 2
        ReserveSeatRequest req2 = new ReserveSeatRequest(List.of("A3"), "idem-user-beta-2");
        DomainConflictException ex = assertThrows(DomainConflictException.class, () ->
                bookingService.reserveSeats(show.getId(), req2, "user-beta", null));
        assertEquals("PER_USER_LIMIT_EXCEEDED", ex.getReason());
    }

    @Test
    void testCancelReservationAndSeatRelease() {
        CreateShowRequest showReq = new CreateShowRequest("jazz-festival", List.of("C1", "C2"), 30000L, 4);
        ShowResponse show = bookingService.createShow(showReq);

        UserContext.setUser("user-gamma", false);
        ReserveSeatRequest reserveReq = new ReserveSeatRequest(List.of("C1"), "idem-cancel-1");
        ReserveSeatResponse res = bookingService.reserveSeats(show.getId(), reserveReq, "user-gamma", null);

        // Non-owner cannot cancel
        UserContext.setUser("user-intruder", false);
        assertThrows(ForbiddenException.class, () ->
                bookingService.cancelReservation(res.getReservationId(), "user-intruder"));

        // Owner cancels
        UserContext.setUser("user-gamma", false);
        CancelReservationResponse cancelRes = bookingService.cancelReservation(res.getReservationId(), "user-gamma");
        assertEquals("cancelled", cancelRes.getStatus());
        assertEquals(List.of("C1"), cancelRes.getFreedSeats());

        // Seat C1 must now be available again for another user
        UserContext.setUser("user-delta", false);
        ReserveSeatRequest rebookReq = new ReserveSeatRequest(List.of("C1"), "idem-rebook-1");
        ReserveSeatResponse rebookRes = bookingService.reserveSeats(show.getId(), rebookReq, "user-delta", null);
        assertNotNull(rebookRes.getReservationId());
        assertEquals("user-delta", rebookRes.getUserId());
    }
}
