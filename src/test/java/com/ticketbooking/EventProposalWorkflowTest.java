package com.ticketbooking;

import com.ticketbooking.dto.*;
import com.ticketbooking.exception.ForbiddenException;
import com.ticketbooking.model.EventRequestStatus;
import com.ticketbooking.security.UserContext;
import com.ticketbooking.service.BookingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class EventProposalWorkflowTest {

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
    void testProposalSubmissionAndAdminApprovalWorkflow() {
        String organizer = "organizer-jane";
        String admin = "admin";
        String customer = "customer-bob";

        // 1. Organizer submits an event proposal
        SubmitEventProposalRequest proposalReq = new SubmitEventProposalRequest(
                "Indie Rock Fest",
                List.of("A1", "A2", "A3", "B1", "B2"),
                35000L,
                2
        );

        UserContext.setUser(organizer, false);
        EventProposalResponse proposal = bookingService.submitEventProposal(proposalReq, organizer);

        assertNotNull(proposal.getId());
        assertEquals("Indie Rock Fest", proposal.getName());
        assertEquals(EventRequestStatus.PENDING, proposal.getStatus());
        assertEquals(organizer, proposal.getRequestedBy());
        assertNull(proposal.getShowId());

        // 2. Organizer sees their proposal
        List<EventProposalResponse> organizerProposals = bookingService.getEventProposals(organizer, false, null);
        assertEquals(1, organizerProposals.size());
        assertEquals(proposal.getId(), organizerProposals.get(0).getId());

        // 3. Admin sees pending proposals and approves it
        UserContext.setUser(admin, true);
        List<EventProposalResponse> adminPending = bookingService.getEventProposals(admin, true, EventRequestStatus.PENDING);
        assertTrue(adminPending.stream().anyMatch(p -> p.getId().equals(proposal.getId())));

        EventProposalResponse approved = bookingService.approveEventProposal(proposal.getId(), admin);
        assertEquals(EventRequestStatus.APPROVED, approved.getStatus());
        assertEquals(admin, approved.getReviewedBy());
        assertNotNull(approved.getShowId());

        String showId = approved.getShowId();

        // 4. Customer reserves seats on the newly created show
        UserContext.setUser(customer, false);
        ReserveSeatRequest reserveReq = new ReserveSeatRequest(List.of("A1", "A2"), "cust-bob-idem-1");
        ReserveSeatResponse res = bookingService.reserveSeats(showId, reserveReq, customer, null);
        assertNotNull(res.getReservationId());

        // 5. Normal customer cannot see other customers' identity details
        UserContext.setUser("other-customer", false);
        ShowResponse normalView = bookingService.getShowState(showId);
        assertEquals(showId, normalView.getId());
        assertEquals(organizer, normalView.getOwnerUserId());
        for (SeatDto seat : normalView.getSeats()) {
            assertNull(seat.getBookedBy(), "Normal user must not see booked_by for privacy");
        }

        // 6. The Event Organizer (Owner/Manager) CAN see customer identity & reservation IDs
        UserContext.setUser(organizer, false);
        ShowResponse managerView = bookingService.getShowState(showId);
        SeatDto seatA1 = managerView.getSeats().stream().filter(s -> s.getSeatNumber().equals("A1")).findFirst().orElseThrow();
        assertEquals("confirmed", seatA1.getStatus());
        assertEquals(customer, seatA1.getBookedBy());
        assertEquals(res.getReservationId(), seatA1.getReservationId());

        // 7. Event Organizer can cancel customer bookings on their event
        CancelReservationResponse cancelRes = bookingService.cancelReservation(res.getReservationId(), organizer);
        assertEquals("cancelled", cancelRes.getStatus());

        // Seat A1 is now available again
        ShowResponse postCancelView = bookingService.getShowState(showId);
        SeatDto seatA1After = postCancelView.getSeats().stream().filter(s -> s.getSeatNumber().equals("A1")).findFirst().orElseThrow();
        assertEquals("available", seatA1After.getStatus());
    }

    @Test
    void testAdminRejectionFlow() {
        String organizer = "organizer-mark";
        String admin = "admin";

        SubmitEventProposalRequest proposalReq = new SubmitEventProposalRequest(
                "Spam / Duplicate Event",
                List.of("X1", "X2"),
                1000L,
                2
        );

        UserContext.setUser(organizer, false);
        EventProposalResponse proposal = bookingService.submitEventProposal(proposalReq, organizer);

        UserContext.setUser(admin, true);
        EventProposalResponse rejected = bookingService.rejectEventProposal(
                proposal.getId(),
                admin,
                "Incomplete event details and duplicate venue timing"
        );

        assertEquals(EventRequestStatus.REJECTED, rejected.getStatus());
        assertEquals(admin, rejected.getReviewedBy());
        assertEquals("Incomplete event details and duplicate venue timing", rejected.getRejectionReason());
        assertNull(rejected.getShowId());
    }

    @Test
    void testNonAdminCannotApprove() {
        String organizer = "organizer-sam";
        SubmitEventProposalRequest proposalReq = new SubmitEventProposalRequest(
                "Sam Festival",
                List.of("S1"),
                5000L,
                1
        );

        UserContext.setUser(organizer, false);
        EventProposalResponse proposal = bookingService.submitEventProposal(proposalReq, organizer);

        // Another regular user tries to approve
        UserContext.setUser("intruder-user", false);
        assertThrows(ForbiddenException.class, () -> {
            bookingService.approveEventProposal(proposal.getId(), "intruder-user");
        });
    }
}
