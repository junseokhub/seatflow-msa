package com.seatflow.show.repository;

import com.seatflow.common.test.composition.MongoContainerSupport;
import com.seatflow.show.domain.Outbox;
import com.seatflow.show.domain.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboxRepository는 커스텀 쿼리가 없는 순수 MongoRepository라 검증할 분기
 * 로직 자체는 없다. 그래도 다음 두 가지는 확인해둘 가치가 있다:
 *   1. Outbox 문서가 실제로 저장/조회 가능한 구조인지 (필드 매핑 문제 조기 발견)
 *   2. @PrePersist류의 초기값(status=PENDING, retryCount=0 등)이 실제로
 *      MongoDB 저장 시 정상 반영되는지 — MongoOutboxStoreIntegrationTest가
 *      이미 간접 검증하지만, 이 계층만 따로 최소 확인해두면 향후 커스텀
 *      쿼리가 추가될 때 이 파일에 이어서 검증을 붙이기 쉬워진다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class OutboxRepositoryTest implements MongoContainerSupport {
    static {
        MongoContainerSupport.startContainer();
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MongoContainerSupport.MONGO::getReplicaSetUrl);
    }

    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
    }

    private Outbox outbox(String eventId) {
        return Outbox.builder()
                .eventId(eventId)
                .eventType("show.created")
                .messageKey("show-1")
                .payload("{}")
                .build();
    }

    @Test
    @DisplayName("save() 후 반환된 객체에는 MongoDB가 생성한 id가 채워져 있다")
    void saveReturnsObjectWithGeneratedId() {
        Outbox saved = outboxRepository.save(outbox("event-1"));

        assertThat(saved.getId()).isNotBlank();
    }

    @Test
    @DisplayName("저장 직후 status는 PENDING, retryCount는 0으로 초기화된다")
    void initialStateIsPendingWithZeroRetryCount() {
        Outbox saved = outboxRepository.save(outbox("event-1"));

        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("findById()로 저장된 Outbox를 정확히 찾는다")
    void findByIdFindsCorrectOutbox() {
        Outbox saved = outboxRepository.save(outbox("event-1"));

        Optional<Outbox> result = outboxRepository.findById(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getEventId()).isEqualTo("event-1");
    }

    @Test
    @DisplayName("findAll()은 저장된 모든 Outbox 문서를 반환한다")
    void findAllReturnsAllOutboxes() {
        outboxRepository.save(outbox("event-1"));
        outboxRepository.save(outbox("event-2"));

        List<Outbox> result = outboxRepository.findAll();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("deleteById() 후에는 findById()가 빈 결과를 반환한다")
    void deleteByIdRemovesOutbox() {
        Outbox saved = outboxRepository.save(outbox("event-1"));

        outboxRepository.deleteById(saved.getId());

        assertThat(outboxRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("payload(JSON 문자열)가 긴 내용이어도 그대로 저장/복원된다 (문자열 필드 손실 없음)")
    void longPayloadStringIsPersistedWithoutLoss() {
        String longPayload = "{" + "\"data\":\"" + "x".repeat(5000) + "\"}";
        Outbox saved = outboxRepository.save(Outbox.builder()
                .eventId("event-1").eventType("show.created")
                .messageKey("show-1").payload(longPayload)
                .build());

        Outbox result = outboxRepository.findById(saved.getId()).orElseThrow();
        assertThat(result.getPayload()).isEqualTo(longPayload);
    }
}