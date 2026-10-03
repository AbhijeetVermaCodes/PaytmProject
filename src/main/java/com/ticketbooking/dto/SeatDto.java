package com.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SeatDto {

    @JsonProperty("seat_number")
    private String seatNumber;

    @JsonProperty("status")
    private String status;

    public SeatDto() {
    }

    public SeatDto(String seatNumber, String status) {
        this.seatNumber = seatNumber;
        this.status = status;
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
}
