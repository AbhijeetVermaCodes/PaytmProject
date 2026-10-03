package com.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RejectProposalRequest {

    @JsonProperty("reason")
    private String reason;

    public RejectProposalRequest() {
    }

    public RejectProposalRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
