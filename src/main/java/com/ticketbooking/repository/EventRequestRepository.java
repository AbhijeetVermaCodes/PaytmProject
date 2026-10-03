package com.ticketbooking.repository;

import com.ticketbooking.model.EventRequest;
import com.ticketbooking.model.EventRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRequestRepository extends JpaRepository<EventRequest, String> {
    List<EventRequest> findByRequestedByOrderByCreatedAtDesc(String requestedBy);
    List<EventRequest> findAllByOrderByCreatedAtDesc();
    List<EventRequest> findByStatusOrderByCreatedAtDesc(EventRequestStatus status);
}
