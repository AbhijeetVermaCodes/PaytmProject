package com.ticketbooking.repository;

import com.ticketbooking.model.Seat;
import com.ticketbooking.model.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, String> {

    List<Seat> findByShowIdOrderBySeatNumberAsc(String showId);

    long countByShowIdAndStatus(String showId, SeatStatus status);

    long countByShowId(String showId);

    Optional<Seat> findByShowIdAndSeatNumber(String showId, String seatNumber);

    /**
     * Acquires pessimistic row-level write locks (SELECT ... FOR UPDATE) on the requested seats.
     * The ORDER BY s.seatNumber ASC guarantees deterministic global lock ordering across all concurrent
     * transactions, mathematically eliminating deadlocks during multi-seat booking contentions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.show.id = :showId AND s.seatNumber IN :seatNumbers ORDER BY s.seatNumber ASC")
    List<Seat> findByShowIdAndSeatNumberInWithLock(
            @Param("showId") String showId,
            @Param("seatNumbers") Collection<String> seatNumbers
    );

    /**
     * Finds and locks seats whose holds have expired for automated background recovery.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.status = com.ticketbooking.model.SeatStatus.HELD AND s.heldUntil <= :now ORDER BY s.seatNumber ASC")
    List<Seat> findExpiredHoldsWithLock(@Param("now") Instant now);
}
