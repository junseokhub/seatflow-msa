package com.seatflow.user.inbox;

import com.seatflow.user.inbox.domain.ProcessedEvent;
import com.seatflow.user.inbox.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    @Modifying
    @Query(value = """
        INSERT IGNORE INTO processed_event (consumer_group, event_id, event_type, processed_at)
        VALUES (:consumerGroup, :eventId, :eventType, :processedAt)
        """, nativeQuery = true)
    int insertIfAbsent(@Param("consumerGroup") String consumerGroup,
                       @Param("eventId") String eventId,
                       @Param("eventType") String eventType,
                       @Param("processedAt") LocalDateTime processedAt);
}