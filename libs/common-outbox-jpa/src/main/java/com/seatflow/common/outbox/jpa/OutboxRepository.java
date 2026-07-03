package com.seatflow.common.outbox.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    @Query(value = """
        SELECT * FROM outbox
        WHERE status = 'PENDING'
          AND next_retry_at <= :now
        ORDER BY id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Outbox> findPendingForUpdate(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM outbox
        WHERE status = 'PUBLISHING'
          AND publishing_at < :threshold
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Outbox> findStuckPublishing(@Param("threshold") LocalDateTime threshold);

    @Query(value = """
        SELECT * FROM outbox
        WHERE status = 'FAILED'
        ORDER BY id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Outbox> findFailedForUpdate(@Param("limit") int limit);

    long countByStatus(OutboxStatus status);

    @Modifying
    @Query(value = """
            DELETE FROM outbox
            WHERE status = 'PUBLISHED'
              AND published_at < :threshold
            LIMIT :limit
            """, nativeQuery = true)
    int deletePublishedBefore(@Param("threshold") Instant threshold,
                              @Param("limit") int limit);
}