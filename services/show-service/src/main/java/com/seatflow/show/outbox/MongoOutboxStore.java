package com.seatflow.show.outbox;

import com.seatflow.common.outbox.OutboxBackoff;
import com.seatflow.common.outbox.OutboxMessage;
import com.seatflow.common.outbox.OutboxStatus;
import com.seatflow.common.outbox.OutboxStore;
import com.seatflow.show.domain.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * OutboxStore의 Mongo 구현(어댑터). 동시성 제어는 findAndModify로 한다
 * (JPA의 FOR UPDATE SKIP LOCKED에 대응). 백오프 정책은 공통 OutboxBackoff 사용.
 * 실패/격리 흔적은 outbox 상태(status, retryCount)로 남는다.
 */
@Service
@RequiredArgsConstructor
public class MongoOutboxStore implements OutboxStore {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<? extends OutboxMessage> claimPending(int limit) {
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

    @Override
    public void markPublished(String id) {
        Query query = new Query(Criteria.where("_id").is(id)
                .and("status").is(OutboxStatus.PUBLISHING));
        Update update = new Update()
                .set("status", OutboxStatus.PUBLISHED)
                .set("publishedAt", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, Outbox.class);
    }

    @Override
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

    @Override
    public void recoverStuck(int minutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);
        Query query = new Query(Criteria.where("status").is(OutboxStatus.PUBLISHING)
                .and("publishingAt").lt(threshold));
        Update update = new Update()
                .set("status", OutboxStatus.PENDING)
                .set("nextRetryAt", LocalDateTime.now());
        mongoTemplate.updateMulti(query, update, Outbox.class);
    }

    @Override
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