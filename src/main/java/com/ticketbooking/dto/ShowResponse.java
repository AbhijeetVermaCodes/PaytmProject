package com.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ShowResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("price_paise")
    private long pricePaise;

    @JsonProperty("per_user_limit")
    private int perUserLimit;

    @JsonProperty("total_seats")
    private int totalSeats;

    @JsonProperty("available_seats")
    private long availableSeats;

    @JsonProperty("held_seats")
    private long heldSeats;

    @JsonProperty("confirmed_seats")
    private long confirmedSeats;

    @JsonProperty("reconciliation_valid")
    private boolean reconciliationValid;

    @JsonProperty("owner_user_id")
    private String ownerUserId;

    @JsonProperty("seats")
    private List<SeatDto> seats;

    public ShowResponse() {
    }

    public ShowResponse(String id, String name, long pricePaise, int perUserLimit, int totalSeats,
                        long availableSeats, long heldSeats, long confirmedSeats, String ownerUserId, List<SeatDto> seats) {
        this.id = id;
        this.name = name;
        this.pricePaise = pricePaise;
        this.perUserLimit = perUserLimit;
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
        this.heldSeats = heldSeats;
        this.confirmedSeats = confirmedSeats;
        this.reconciliationValid = (availableSeats + heldSeats + confirmedSeats) == totalSeats;
        this.ownerUserId = ownerUserId;
        this.seats = seats;
    }

    public ShowResponse(String id, String name, long pricePaise, int perUserLimit, int totalSeats,
                        long availableSeats, long heldSeats, long confirmedSeats, List<SeatDto> seats) {
        this(id, name, pricePaise, perUserLimit, totalSeats, availableSeats, heldSeats, confirmedSeats, null, seats);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(long pricePaise) {
        this.pricePaise = pricePaise;
    }

    public int getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(int perUserLimit) {
        this.perUserLimit = perUserLimit;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public long getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(long availableSeats) {
        this.availableSeats = availableSeats;
    }

    public long getHeldSeats() {
        return heldSeats;
    }

    public void setHeldSeats(long heldSeats) {
        this.heldSeats = heldSeats;
    }

    public long getConfirmedSeats() {
        return confirmedSeats;
    }

    public void setConfirmedSeats(long confirmedSeats) {
        this.confirmedSeats = confirmedSeats;
    }

    public boolean isReconciliationValid() {
        return reconciliationValid;
    }

    public void setReconciliationValid(boolean reconciliationValid) {
        this.reconciliationValid = reconciliationValid;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public List<SeatDto> getSeats() {
        return seats;
    }

    public void setSeats(List<SeatDto> seats) {
        this.seats = seats;
    }
}
