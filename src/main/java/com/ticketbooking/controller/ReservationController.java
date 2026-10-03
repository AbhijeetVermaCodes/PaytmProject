package com.ticketbooking.controller;

import com.ticketbooking.dto.CancelReservationResponse;
import com.ticketbooking.exception.UnauthorizedException;
import com.ticketbooking.security.UserContext;
import com.ticketbooking.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final BookingService bookingService;

    public ReservationController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * 3. Release / cancel a reservation — POST /reservations/{id}/cancel (owner only)
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<CancelReservationResponse> cancelReservation(
            @PathVariable("id") String reservationId) {

        String userId = UserContext.getCurrentUser();
        if (!StringUtils.hasText(userId)) {
            throw new UnauthorizedException("Authentication token required to cancel reservation");
        }

        CancelReservationResponse response = bookingService.cancelReservation(reservationId, userId);
        return ResponseEntity.ok(response);
    }
}
