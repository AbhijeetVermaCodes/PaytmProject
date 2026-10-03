package com.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticketbooking.model.EventRequestStatus;

import java.time.Instant;
import java.util.List;

public class EventProposalResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("seats")
    private List<String> seats;

    @JsonProperty("price_paise")
    private long pricePaise;

    @JsonProperty("per_user_limit")
    private int perUserLimit;

    @JsonProperty("requested_by")
    private String requestedBy;

    @JsonProperty("status")
    private EventRequestStatus status;

    @JsonProperty("reviewed_by")
    private String reviewedBy;

    @JsonProperty("show_id")
    private String showId;

    @JsonProperty("rejection_reason")
    private String rejectionReason;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("reviewed_at")
    private Instant reviewedAt;

    public EventProposalResponse() {
    }

    public EventProposalResponse(String id, String name, List<String> seats, long pricePaise, int perUserLimit,
                                 String requestedBy, EventRequestStatus status, String reviewedBy,
                                 String showId, String rejectionReason, Instant createdAt, Instant reviewedAt) {
        this.id = id;
        this.name = name;
        this.seats = seats;
        this.pricePaise = pricePaise;
        this.perUserLimit = perUserLimit;
        this.requestedBy = requestedBy;
        this.status = status;
        this.reviewedBy = reviewedBy;
        this.showId = showId;
        this.rejectionReason = rejectionReason;
        this.createdAt = createdAt;
        this.reviewedAt = reviewedAt;
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

    public int getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(int perUserLimit) {
        this.perUserLimit = perUserLimit;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public EventRequestStatus getStatus() {
        return status;
    }

    public void setStatus(EventRequestStatus status) {
        this.status = status;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getShowId() {
        return showId;
    }

    public void setShowId(String showId) {
        this.showId = showId;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
