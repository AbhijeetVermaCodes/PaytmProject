package com.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ReserveSeatResponse {

    @JsonProperty("reservation_id")
    private String reservationId;

    @JsonProperty("show_id")
    private String showId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("seats")
    private List<String> seats;

    @JsonProperty("amount_paise")
    private long amountPaise;

    @JsonProperty("status")
    private String status;

    public ReserveSeatResponse() {
    }

    public ReserveSeatResponse(String reservationId, String showId, String userId, List<String> seats, long amountPaise, String status) {
        this.reservationId = reservationId;
        this.showId = showId;
        this.userId = userId;
        this.seats = seats;
        this.amountPaise = amountPaise;
        this.status = status;
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

    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public void setAmountPaise(long amountPaise) {
        this.amountPaise = amountPaise;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
