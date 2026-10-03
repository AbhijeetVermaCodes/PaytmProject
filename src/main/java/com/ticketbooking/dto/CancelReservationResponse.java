package com.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class CancelReservationResponse {

    @JsonProperty("reservation_id")
    private String reservationId;

    @JsonProperty("show_id")
    private String showId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("freed_seats")
    private List<String> freedSeats;

    public CancelReservationResponse() {
    }

    public CancelReservationResponse(String reservationId, String showId, String userId, String status, List<String> freedSeats) {
        this.reservationId = reservationId;
        this.showId = showId;
        this.userId = userId;
        this.status = status;
        this.freedSeats = freedSeats;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getShowId() {
        return showId;
    }

    public void setShowId(String showId) {
        this.showId = showId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getFreedSeats() {
        return freedSeats;
    }

    public void setFreedSeats(List<String> freedSeats) {
        this.freedSeats = freedSeats;
    }
}
