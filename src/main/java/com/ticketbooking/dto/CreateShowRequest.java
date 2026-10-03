package com.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class CreateShowRequest {

    @NotBlank(message = "Show name cannot be blank")
    private String name;

    @NotEmpty(message = "Seats list cannot be empty")
    private List<String> seats;

    @JsonProperty("price_paise")
    @Min(value = 1, message = "Price in paise must be positive")
    private long pricePaise;

    @JsonProperty("per_user_limit")
    @Min(value = 1, message = "Per user limit must be at least 1")
    private Integer perUserLimit;

    public CreateShowRequest() {
    }

    public CreateShowRequest(String name, List<String> seats, long pricePaise, Integer perUserLimit) {
        this.name = name;
        this.seats = seats;
        this.pricePaise = pricePaise;
        this.perUserLimit = perUserLimit;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(long pricePaise) {
        this.pricePaise = pricePaise;
    }

    public Integer getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(Integer perUserLimit) {
        this.perUserLimit = perUserLimit;
    }
}
