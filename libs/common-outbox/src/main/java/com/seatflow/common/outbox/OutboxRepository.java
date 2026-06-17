package com.seatflow.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    @Query(value = """
        SELECT * FROM outbox
        WHERE status = 'PENDING'
        ORDER BY id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Outbox> findPendingForUpdate(@Param("limit") int limit);

    @Query(value = """
        SELECT * FROM outbox
        WHERE status = 'PUBLISHING'
        AND publishing_at < :threshold
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Outbox> findStuckPublishing(@Param("threshold") LocalDateTime threshold);
}