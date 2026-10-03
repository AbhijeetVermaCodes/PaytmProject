package com.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeatDto {

    @JsonProperty("seat_number")
    private String seatNumber;

    @JsonProperty("status")
    private String status;

    @JsonProperty("booked_by")
    private String bookedBy;

    @JsonProperty("reservation_id")
    private String reservationId;

    public SeatDto() {
    }

    public SeatDto(String seatNumber, String status) {
        this(seatNumber, status, null, null);
    }

    public SeatDto(String seatNumber, String status, String bookedBy, String reservationId) {
        this.seatNumber = seatNumber;
        this.status = status;
        this.bookedBy = bookedBy;
        this.reservationId = reservationId;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBookedBy() {
        return bookedBy;
    }

    public void setBookedBy(String bookedBy) {
        this.bookedBy = bookedBy;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }
}
