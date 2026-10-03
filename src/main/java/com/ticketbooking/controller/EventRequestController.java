package com.ticketbooking.controller;

import com.ticketbooking.dto.EventProposalResponse;
import com.ticketbooking.dto.RejectProposalRequest;
import com.ticketbooking.dto.SubmitEventProposalRequest;
import com.ticketbooking.exception.UnauthorizedException;
import com.ticketbooking.model.EventRequestStatus;
import com.ticketbooking.security.UserContext;
import com.ticketbooking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/event-requests")
public class EventRequestController {

    private final BookingService bookingService;

    public EventRequestController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Submit an event creation request/proposal (authenticated regular or admin user)
     */
    @PostMapping
    public ResponseEntity<EventProposalResponse> submitProposal(@Valid @RequestBody SubmitEventProposalRequest request) {
        String userId = UserContext.getCurrentUser();
        if (!StringUtils.hasText(userId)) {
            throw new UnauthorizedException("Authentication token required to submit an event proposal");
        }

        EventProposalResponse response = bookingService.submitEventProposal(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List event proposals.
     * Admin sees all proposals; regular users see only their own.
     */
    @GetMapping
    public ResponseEntity<List<EventProposalResponse>> getProposals(
            @RequestParam(value = "status", required = false) EventRequestStatus status) {
        String userId = UserContext.getCurrentUser();
        if (!StringUtils.hasText(userId)) {
            throw new UnauthorizedException("Authentication token required to view event proposals");
        }

        List<EventProposalResponse> list = bookingService.getEventProposals(userId, UserContext.isAdmin(), status);
        return ResponseEntity.ok(list);
    }

    /**
     * Get a specific event proposal by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<EventProposalResponse> getProposalById(@PathVariable("id") String id) {
        String userId = UserContext.getCurrentUser();
        if (!StringUtils.hasText(userId)) {
            throw new UnauthorizedException("Authentication token required to view event proposals");
        }

        EventProposalResponse response = bookingService.getEventProposalById(id, userId, UserContext.isAdmin());
        return ResponseEntity.ok(response);
    }

    /**
     * Admin approves an event proposal -> creates the live show with requester as owner
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<EventProposalResponse> approveProposal(@PathVariable("id") String id) {
        String userId = UserContext.getCurrentUser();
        if (!StringUtils.hasText(userId)) {
            throw new UnauthorizedException("Authentication required");
        }

        EventProposalResponse response = bookingService.approveEventProposal(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Admin rejects an event proposal
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<EventProposalResponse> rejectProposal(
            @PathVariable("id") String id,
            @RequestBody(required = false) RejectProposalRequest request) {
        String userId = UserContext.getCurrentUser();
        if (!StringUtils.hasText(userId)) {
            throw new UnauthorizedException("Authentication required");
        }

        String reason = request != null ? request.getReason() : null;
        EventProposalResponse response = bookingService.rejectEventProposal(id, userId, reason);
        return ResponseEntity.ok(response);
    }
}
