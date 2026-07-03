package com.seatflow.show.outbox;

import com.mongodb.client.result.DeleteResult;
import com.seatflow.show.domain.Outbox;
import com.seatflow.show.domain.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Mongo 전용 Outbox 저장소(DAO). 동시성 제어는 findAndModify로 한다.
 * common-outbox 모듈에 대한 의존이 없는, show-service 단독 구현이다.
 * (다른 구현체로 교체될 일이 없으므로 인터페이스 추상화 없이 바로 구현한다.)
 */
@Component
@RequiredArgsConstructor
public class MongoOutboxStore {

    private final MongoTemplate mongoTemplate;

    public List<Outbox> claimPending(int limit) {
        List<Outbox> claimed = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < limit; i++) {
            Query query = new Query(
                    Criteria.where("status").is(OutboxStatus.PENDING)
                            .and("nextRetryAt").lte(now))
                    .with(Sort.by("nextRetryAt"));

            Update update = new Update()
                    .set("status", OutboxStatus.PUBLISHING)
                    .set("publishingAt", LocalDateTime.now());

            Outbox doc = mongoTemplate.findAndModify(
                    query, update,
                    FindAndModifyOptions.options().returnNew(true),
                    Outbox.class);

            if (doc == null) break;
            claimed.add(doc);
        }
        return claimed;
    }

    public int deletePublishedBefore(int retentionHours, int limit) {
        Instant threshold = Instant.now().minus(retentionHours, ChronoUnit.HOURS);

        // 1) 지울 대상 _id 만 limit개 선별 (인덱스: status + publishedAt)
        Query selectQuery = new Query(
                Criteria.where("status").is(OutboxStatus.PUBLISHED)
                        .and("publishedAt").lt(threshold))
                .limit(limit);
        selectQuery.fields().include("_id");

        List<Outbox> targets = mongoTemplate.find(selectQuery, Outbox.class);
        if (targets.isEmpty()) {
            return 0;
        }

        // 2) 선별한 _id 들만 삭제
        List<String> ids = targets.stream().map(Outbox::getId).toList();
        DeleteResult result = mongoTemplate.remove(
                new Query(Criteria.where("_id").in(ids)), Outbox.class);
        return (int) result.getDeletedCount();
    }

    public void markPublished(String id) {
        Query query = new Query(Criteria.where("_id").is(id)
                .and("status").is(OutboxStatus.PUBLISHING));
        Update update = new Update()
                .set("status", OutboxStatus.PUBLISHED)
                .set("publishedAt", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, Outbox.class);
    }

    public void markFailedOrRetry(String id) {
        Outbox doc = mongoTemplate.findById(id, Outbox.class);
        if (doc == null) return;

        if (OutboxBackoff.isExceeded(doc.getRetryCount())) {
            mongoTemplate.updateFirst(
                    new Query(Criteria.where("_id").is(id)),
                    new Update().set("status", OutboxStatus.FAILED),
                    Outbox.class);
        } else {
            int nextCount = doc.getRetryCount() + 1;
            mongoTemplate.updateFirst(
                    new Query(Criteria.where("_id").is(id)),
                    new Update()
                            .set("status", OutboxStatus.PENDING)
                            .set("retryCount", nextCount)
                            .set("nextRetryAt", OutboxBackoff.nextRetryAt(nextCount)),
                    Outbox.class);
        }
    }

    public void recoverStuck(int minutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);
        Query query = new Query(Criteria.where("status").is(OutboxStatus.PUBLISHING)
                .and("publishingAt").lt(threshold));
        Update update = new Update()
                .set("status", OutboxStatus.PENDING)
                .set("nextRetryAt", LocalDateTime.now());
        mongoTemplate.updateMulti(query, update, Outbox.class);
    }

    public long redriveFailed(int limit) {
        long count = 0;
        for (int i = 0; i < limit; i++) {
            Query query = new Query(Criteria.where("status").is(OutboxStatus.FAILED));
            Update update = new Update()
                    .set("status", OutboxStatus.PENDING)
                    .set("retryCount", 0)
                    .set("nextRetryAt", LocalDateTime.now());
            Outbox doc = mongoTemplate.findAndModify(
                    query, update,
                    FindAndModifyOptions.options().returnNew(true),
                    Outbox.class);
            if (doc == null) break;
            count++;
        }
        return count;
    }
}