package com.ticketbooking.repository;

import com.ticketbooking.model.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShowRepository extends JpaRepository<Show, String> {
    Optional<Show> findById(String id);
    List<Show> findAllByOrderByCreatedAtDesc();
}

