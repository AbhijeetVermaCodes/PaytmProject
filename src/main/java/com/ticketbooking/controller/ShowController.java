package com.ticketbooking.controller;

import com.ticketbooking.dto.*;
import com.ticketbooking.exception.UnauthorizedException;
import com.ticketbooking.security.UserContext;
import com.ticketbooking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final BookingService bookingService;

    public ShowController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * 1. Create a show — POST /shows (admin)
     */
    @PostMapping
    public ResponseEntity<ShowResponse> createShow(@Valid @RequestBody CreateShowRequest request) {
        // Admin or authenticated check (optional authorization toggle)
        ShowResponse response = bookingService.createShow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all shows — GET /shows
     */
    @GetMapping
    public ResponseEntity<java.util.List<ShowResponse>> getAllShows() {
        java.util.List<ShowResponse> responses = bookingService.getAllShows();
        return ResponseEntity.ok(responses);
    }

    /**
     * 4. Show state — GET /shows/{id}
     * Returns per-seat status and counts. Invariant holds: available + held + confirmed == total_seats.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ShowResponse> getShow(@PathVariable("id") String id) {
        ShowResponse response = bookingService.getShowState(id);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. Reserve a seat — POST /shows/{id}/reserve (authenticated user)
     * Identity comes from auth token, not request body.
     */
    @PostMapping("/{id}/reserve")
    public ResponseEntity<ReserveSeatResponse> reserveSeats(
            @PathVariable("id") String showId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @Valid @RequestBody ReserveSeatRequest request) {

        String userId = UserContext.getCurrentUser();
        if (!StringUtils.hasText(userId)) {
            throw new UnauthorizedException("Authentication token required in Authorization header (e.g. 'Bearer user-123')");
        }

        ReserveSeatResponse response = bookingService.reserveSeats(showId, request, userId, idempotencyHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
