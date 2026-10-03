package com.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @JsonProperty("error")
    private String error;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("message")
    private String message;

    @JsonProperty("conflicting_seats")
    private List<String> conflictingSeats;

    @JsonProperty("timestamp")
    private Instant timestamp;

    public ErrorResponse() {
    }

    public ErrorResponse(String error, String reason, String message) {
        this.error = error;
        this.reason = reason;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public ErrorResponse(String error, String reason, String message, List<String> conflictingSeats) {
        this.error = error;
        this.reason = reason;
        this.message = message;
        this.conflictingSeats = conflictingSeats;
        this.timestamp = Instant.now();
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getConflictingSeats() {
        return conflictingSeats;
    }

    public void setConflictingSeats(List<String> conflictingSeats) {
        this.conflictingSeats = conflictingSeats;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
