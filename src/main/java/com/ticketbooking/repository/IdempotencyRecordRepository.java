package com.ticketbooking.repository;

import com.ticketbooking.model.IdempotencyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IdempotencyRecord i WHERE i.userId = :userId AND i.idempotencyKey = :idempotencyKey")
    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKeyWithLock(
            @Param("userId") String userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}
