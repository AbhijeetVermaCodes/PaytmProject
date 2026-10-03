package com.ticketbooking.repository;

import com.ticketbooking.model.Reservation;
import com.ticketbooking.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {

    Optional<Reservation> findByIdAndUserId(String id, String userId);

    List<Reservation> findByShowId(String showId);

    List<Reservation> findByUserId(String userId);

    Optional<Reservation> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    /**
     * Counts the total number of active seats (CONFIRMED or HELD) currently held by the user for this show.
     * Used within the reservation transaction to enforce per-user quota limits atomically.
     */
    @Query("SELECT COALESCE(SUM(SIZE(r.seats)), 0) FROM Reservation r WHERE r.userId = :userId AND r.showId = :showId AND r.status IN :statuses")
    int countActiveSeatsForUser(
            @Param("userId") String userId,
            @Param("showId") String showId,
            @Param("statuses") Collection<ReservationStatus> statuses
    );
}
