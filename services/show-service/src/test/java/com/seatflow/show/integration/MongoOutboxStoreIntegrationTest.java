package com.seatflow.show.integration;

import com.mongodb.client.result.UpdateResult;
import com.seatflow.common.test.composition.MongoContainerSupport;
import com.seatflow.show.domain.Outbox;
import com.seatflow.show.domain.OutboxStatus;
import com.seatflow.show.outbox.MongoOutboxStore;
import com.seatflow.show.outbox.OutboxBackoff;
import com.seatflow.show.repository.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MongoOutboxStore의 findAndModify 기반 동시성 제어를 진짜 MongoDB(replica set) 위에서 검증한다.
 * Mock으로는 findAndModify의 원자성 자체를 확인할 수 없다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MongoOutboxStoreIntegrationTest implements MongoContainerSupport {

    @Autowired
    private MongoOutboxStore outboxStore;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    @org.junit.jupiter.api.BeforeEach
    void cleanUp() {
        mongoTemplate.remove(new Query(), Outbox.class);
    }

    private Outbox pendingOutbox(String eventId) {
        return outboxRepository.save(Outbox.builder()
                .eventId(eventId)
                .eventType("show.created")
                .messageKey("show-1")
                .payload("{}")
                .build());
    }

    @Test
    @DisplayName("claimPending()은 PENDING 상태를 PUBLISHING으로 전이시키고 반환한다")
    void claimPendingTransitionsToPublishing() {
        pendingOutbox("event-1");

        List<Outbox> claimed = outboxStore.claimPending(10);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getStatus()).isEqualTo(OutboxStatus.PUBLISHING);
    }

    @Test
    @DisplayName("claimPending()은 이미 PUBLISHING인 문서를 다시 집지 않는다")
    void claimPendingDoesNotReclaimAlreadyPublishing() {
        pendingOutbox("event-1");
        outboxStore.claimPending(10);   // 첫 번째 클레임으로 PUBLISHING이 됨

        List<Outbox> secondClaim = outboxStore.claimPending(10);

        assertThat(secondClaim).isEmpty();
    }

    @Test
    @DisplayName("limit보다 많은 PENDING이 있어도 limit만큼만 클레임한다")
    void claimPendingRespectsLimit() {
        for (int i = 0; i < 5; i++) {
            pendingOutbox("event-" + i);
        }

        List<Outbox> claimed = outboxStore.claimPending(3);

        assertThat(claimed).hasSize(3);
    }

    @Test
    @DisplayName("nextRetryAt이 미래인 문서는 아직 발행 대상이 아니다")
    void doesNotClaimDocumentsWithFutureRetryTime() {
        Outbox outbox = pendingOutbox("event-1");
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(outbox.getId())),
                new Update().set("nextRetryAt", LocalDateTime.now().plusMinutes(10)),
                Outbox.class);

        List<Outbox> claimed = outboxStore.claimPending(10);

        assertThat(claimed).isEmpty();
    }

    @Test
    @DisplayName("진짜 동시에 여러 스레드가 claimPending을 호출해도 같은 문서를 중복으로 못 집는다")
    void concurrentClaimPendingNeverDoubleClaims() throws InterruptedException {
        int docCount = 20;
        for (int i = 0; i < docCount; i++) {
            pendingOutbox("concurrent-event-" + i);
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ConcurrentLinkedQueue<String> allClaimedIds = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    List<Outbox> claimed = outboxStore.claimPending(10);
                    claimed.forEach(o -> allClaimedIds.add(o.getId()));
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // 총 20건을 5개 스레드가 나눠 가져가되, 겹치는 id가 하나도 없어야 한다.
        assertThat(allClaimedIds).hasSize(docCount);
        assertThat(new java.util.HashSet<>(allClaimedIds)).hasSize(docCount);   // 중복 없음
    }

    @Test
    @DisplayName("markPublished()는 PUBLISHING 상태만 PUBLISHED로 전이시킨다")
    void markPublishedOnlyTransitionsFromPublishing() {
        Outbox outbox = pendingOutbox("event-1");
        outboxStore.claimPending(10);   // PENDING -> PUBLISHING

        outboxStore.markPublished(outbox.getId());

        Outbox result = mongoTemplate.findById(outbox.getId(), Outbox.class);
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(result.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("markPublished()는 PENDING 상태(아직 안 집힌)인 문서는 건드리지 않는다")
    void markPublishedDoesNotAffectStillPendingDocument() {
        Outbox outbox = pendingOutbox("event-1");
        // claimPending을 안 해서 여전히 PENDING인 상태

        outboxStore.markPublished(outbox.getId());

        Outbox result = mongoTemplate.findById(outbox.getId(), Outbox.class);
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.PENDING);   // 안 바뀜
    }

    @Test
    @DisplayName("markFailedOrRetry()는 MAX_RETRY 미만이면 PENDING으로 되돌리고 retryCount를 늘린다")
    void markFailedOrRetryReturnsToPendingBelowMaxRetry() {
        Outbox outbox = pendingOutbox("event-1");
        outboxStore.claimPending(10);

        outboxStore.markFailedOrRetry(outbox.getId());

        Outbox result = mongoTemplate.findById(outbox.getId(), Outbox.class);
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getNextRetryAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("markFailedOrRetry()는 MAX_RETRY에 도달하면 FAILED로 격리한다")
    void markFailedOrRetryIsolatesAtMaxRetry() {
        Outbox outbox = pendingOutbox("event-1");
        /**
         * markFailedOrRetry()는 증가 전 retryCount로 isExceeded()를 판단한다(증가는 재시도 경로에서만 일어남).
         * 그러니 MAX_RETRY 그 자체로 세팅해야 FAILED 분기를 정확히 태울 수 있다.
         * MAX_RETRY - 1로는 아직 재시도 경로(PENDING)를 탄다는 걸 실제로 겪었다.
         */
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(outbox.getId())),
                new Update().set("retryCount", com.seatflow.show.outbox.OutboxBackoff.MAX_RETRY)
                        .set("status", OutboxStatus.PUBLISHING),
                Outbox.class);

        outboxStore.markFailedOrRetry(outbox.getId());

        Outbox result = mongoTemplate.findById(outbox.getId(), Outbox.class);
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    @DisplayName("markFailedOrRetry()는 MAX_RETRY 바로 직전(-1)이면 아직 PENDING으로 재시도시킨다 (경계값)")
    void markFailedOrRetryStillRetriesJustBelowMaxRetry() {
        Outbox outbox = pendingOutbox("event-1");
        UpdateResult updateResult = mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(outbox.getId())),
                new Update().set("retryCount", OutboxBackoff.MAX_RETRY - 1)
                        .set("status", OutboxStatus.PUBLISHING),
                Outbox.class);

        outboxStore.markFailedOrRetry(outbox.getId());

        Outbox result = mongoTemplate.findById(outbox.getId(), Outbox.class);
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(result.getRetryCount()).isEqualTo(com.seatflow.show.outbox.OutboxBackoff.MAX_RETRY);
    }

    @Test
    @DisplayName("recoverStuck()은 임계 시간을 넘겨 PUBLISHING에 멈춰있는 문서를 PENDING으로 되돌린다")
    void recoverStuckRestoresStalePublishingDocuments() {
        Outbox outbox = pendingOutbox("event-1");
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(outbox.getId())),
                new Update().set("status", OutboxStatus.PUBLISHING)
                        .set("publishingAt", LocalDateTime.now().minusMinutes(10)),
                Outbox.class);

        outboxStore.recoverStuck(5);   // 5분 임계값

        Outbox result = mongoTemplate.findById(outbox.getId(), Outbox.class);
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("recoverStuck()은 임계 시간 이내의 PUBLISHING 문서는 건드리지 않는다")
    void recoverStuckDoesNotAffectRecentPublishing() {
        Outbox outbox = pendingOutbox("event-1");
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(outbox.getId())),
                new Update().set("status", OutboxStatus.PUBLISHING)
                        .set("publishingAt", LocalDateTime.now().minusMinutes(1)),
                Outbox.class);

        outboxStore.recoverStuck(5);

        Outbox result = mongoTemplate.findById(outbox.getId(), Outbox.class);
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.PUBLISHING);   // 안 바뀜
    }

    @Test
    @DisplayName("redriveFailed()는 FAILED 상태를 PENDING으로 되돌리고 retryCount를 리셋한다")
    void redriveFailedResetsToPending() {
        Outbox outbox = pendingOutbox("event-1");
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(outbox.getId())),
                new Update().set("status", OutboxStatus.FAILED).set("retryCount", 10),
                Outbox.class);

        long redrivenCount = outboxStore.redriveFailed(10);

        assertThat(redrivenCount).isEqualTo(1);
        Outbox result = mongoTemplate.findById(outbox.getId(), Outbox.class);
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(result.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("deletePublishedBefore()는 보관기한이 지난 PUBLISHED 문서만 삭제한다")
    void deletePublishedBeforeDeletesOnlyOldPublished() {
        Outbox oldOne = pendingOutbox("old-event");
        Outbox recentOne = pendingOutbox("recent-event");
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(oldOne.getId())),
                new Update().set("status", OutboxStatus.PUBLISHED)
                        .set("publishedAt", LocalDateTime.now().minusHours(100)),
                Outbox.class);
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(recentOne.getId())),
                new Update().set("status", OutboxStatus.PUBLISHED)
                        .set("publishedAt", LocalDateTime.now().minusHours(1)),
                Outbox.class);

        int deletedCount = outboxStore.deletePublishedBefore(72, 1000);

        assertThat(deletedCount).isEqualTo(1);
        assertThat(mongoTemplate.findById(oldOne.getId(), Outbox.class)).isNull();
        assertThat(mongoTemplate.findById(recentOne.getId(), Outbox.class)).isNotNull();
    }
}