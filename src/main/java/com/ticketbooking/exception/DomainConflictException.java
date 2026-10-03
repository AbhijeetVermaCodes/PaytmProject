package com.ticketbooking.exception;

import java.util.Collections;
import java.util.List;

public class DomainConflictException extends RuntimeException {

    private final String reason;
    private final List<String> conflictingSeats;

    public DomainConflictException(String reason, String message) {
        super(message);
        this.reason = reason;
        this.conflictingSeats = Collections.emptyList();
    }

    public DomainConflictException(String reason, String message, List<String> conflictingSeats) {
        super(message);
        this.reason = reason;
        this.conflictingSeats = conflictingSeats != null ? conflictingSeats : Collections.emptyList();
    }

    public String getReason() {
        return reason;
    }

    public List<String> getConflictingSeats() {
        return conflictingSeats;
    }
}
