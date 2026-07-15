package com.seatflow.reservation.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.payment.PaymentCompletedEvent;
import com.seatflow.common.event.payment.PaymentRefundFailedEvent;
import com.seatflow.common.event.payment.PaymentRefundedEvent;
import com.seatflow.common.event.seat.SeatHeldEvent;
import com.seatflow.common.event.seat.SeatReleasedEvent;
import com.seatflow.common.event.seat.SeatReservedCompensatedEvent;
import com.seatflow.common.test.composition.KafkaContainerSupport;
import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.reservation.domain.CancelSaga;
import com.seatflow.reservation.domain.CancelSagaStatus;
import com.seatflow.reservation.domain.Reservation;
import com.seatflow.reservation.domain.ReservationStatus;
import com.seatflow.reservation.repository.CancelSagaRepository;
import com.seatflow.reservation.repository.ReservationRepository;
import com.seatflow.reservation.service.CancelSagaOrchestrator;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * reservation-service가 진짜 Kafka 브로커를 통해 발행/구독하는지 검증한다.
 *
 * 발행 검증: outboxAppender로 적재된 이벤트가 OutboxScheduler(1초 폴링)를 거쳐
 * 진짜 Kafka 토픽에 나가는지, 별도의 KafkaConsumer로 직접 폴링해서 확인한다.
 *
 * 구독 검증: 이 테스트가 "다른 서비스인 척" 진짜 KafkaTemplate으로 이벤트를
 * 발행하고, reservation-service의 진짜 @KafkaListener가 그걸 받아서 DB 상태를
 * 정확히 바꾸는지 확인한다.
 *
 * seat-service, payment-service는 실제로 띄우지 않는다 — 그 서비스들의
 * 컨슈머/발행 로직은 각자의 단위 테스트에서 이미 검증했고, 여기서는
 * "reservation-service의 프로듀서/컨슈머가 진짜 Kafka와 올바르게 통신하는가"만
 * 본다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SagaKafkaIntegrationTest implements MysqlContainerSupport, KafkaContainerSupport {

    private static final String SOURCE = "test-simulator";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ObjectMapper kafkaObjectMapper;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private CancelSagaRepository cancelSagaRepository;
    @Autowired
    private CancelSagaOrchestrator cancelSagaOrchestrator;

    private KafkaConsumer<String, String> rawConsumer;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        cancelSagaRepository.deleteAll();

        // OutboxScheduler가 진짜 발행한 걸 직접 확인하기 위한, reservation-service의 진짜 컨슈머 그룹과 무관한 별도 컨슈머(그룹 매번 새로 만들어서 항상 처음부터 읽음).
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaContainerSupport.KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-observer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        rawConsumer = new KafkaConsumer<>(props);

        waitUntilAllListenersReady();   // 진짜 메시지를 발행하기 전에 항상 먼저 확인
    }

    /**
     * ContainerTestUtils.waitForAssignment()로 리스너가 진짜 준비됐는지 @BeforeEach에서 먼저 확인하므로,
     * 이 타임아웃은 준비 안 된 컨슈머를 기다리는 시간이 아니라 순수하게 메시지 처리 + DB 반영 시간만 커버하면 된다.
     */
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    private org.awaitility.core.ConditionFactory awaitCondition() {
        return await().atMost(AWAIT_TIMEOUT).pollInterval(POLL_INTERVAL);
    }

    /**
     * 모든 @KafkaListener 컨테이너가 파티션(테스트 환경은 항상 1개)을 실제로 할당받을 때까지 기다린다.
     * 이걸 안 하면, 컨텍스트는 다 떴다고 봐도 컨슈머가 브로커로부터 파티션을 아직 안 받은 채로 테스트가 메시지를 발행해버려서,
     * 그 메시지를 컨슈머가 영영 못 받는 경우가 있었다.
     * 파일 전체 실행시엔 앞선 테스트들이 이미 워밍업을 시켜놔서 안 드러났지만, 이 테스트 하나만 단독 실행하면 매번 재현됐다.
     */
    private void waitUntilAllListenersReady() {
        registry.getListenerContainers().forEach(container ->
                org.springframework.kafka.test.utils.ContainerTestUtils.waitForAssignment(container, 1));
    }

    private <T> void publish(String topic, String aggregateId, T payload, String eventVersion) {
        EventEnvelope<T> envelope = EventEnvelope.of(topic, eventVersion, SOURCE, aggregateId, payload);
        try {
            String json = kafkaObjectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, aggregateId, json).get();   // 동기 대기, 테스트에서 순서 보장 목적
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Reservation confirmedReservation(String userId, String showId, Long seatId, LocalDateTime showDate) {
        Reservation reservation = Reservation.builder()
                .userId(userId).showId(showId).seatId(seatId)
                .amount(BigDecimal.valueOf(10000)).showDate(showDate)
                .status(ReservationStatus.PENDING)
                .build();
        Reservation saved = reservationRepository.save(reservation);
        saved.confirm();
        return reservationRepository.save(saved);
    }

    @Test
    @DisplayName("정상 흐름: 진짜 Kafka로 SeatHeldEvent를 발행하면, 진짜 컨슈머가 받아서 Reservation을 생성한다")
    void seatHeldEventCreatesReservationViaRealKafka() {
        String userId = "user1";
        String showId = "show-1";
        Long seatId = 1L;
        LocalDateTime showDate = LocalDateTime.now().plusDays(10);

        publish(EventTopic.SEAT_HELD, showId,
                new SeatHeldEvent(userId, showId, seatId, BigDecimal.valueOf(10000), showDate),
                "1.0");

        awaitCondition().untilAsserted(() -> {
            List<Reservation> all = reservationRepository.findByUserId(userId);
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getStatus()).isEqualTo(ReservationStatus.PENDING);
        });
    }

    @Test
    @DisplayName("정상 흐름: 진짜 Kafka로 PaymentCompletedEvent를 발행하면, 예매가 CONFIRMED로 바뀐다")
    void paymentCompletedEventConfirmsReservationViaRealKafka() {
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .userId("user1").showId("show-1").seatId(1L)
                .amount(BigDecimal.valueOf(10000)).showDate(LocalDateTime.now().plusDays(10))
                .status(ReservationStatus.PENDING)
                .build());

        publish(EventTopic.PAYMENT_COMPLETED, String.valueOf(reservation.getId()),
                new PaymentCompletedEvent(reservation.getId(), "user1", BigDecimal.valueOf(10000)),
                "1.0");

        awaitCondition().untilAsserted(() -> {
            Reservation result = reservationRepository.findById(reservation.getId()).orElseThrow();
            assertThat(result.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        });
    }

    @Test
    @DisplayName("취소 발행 검증: startCancellation을 호출하면 SeatReleaseCommand가 진짜 Kafka 토픽에 나간다")
    void startCancellationPublishesSeatReleaseCommandToRealKafka() {
        Reservation reservation = confirmedReservation("user1", "show-1", 1L, LocalDateTime.now().plusDays(15));

        cancelSagaOrchestrator.startCancellation(reservation.getId(), "user1");

        rawConsumer.subscribe(List.of(EventTopic.SEAT_RELEASE_COMMAND));
        awaitCondition().untilAsserted(() -> {
            ConsumerRecords<String, String> records = rawConsumer.poll(Duration.ofMillis(500));
            boolean found = false;
            for (ConsumerRecord<String, String> record : records) {
                if (record.value().contains("\"reservationId\":" + reservation.getId())) {
                    found = true;
                }
            }
            assertThat(found).isTrue();
        });
    }

    @Test
    @DisplayName("취소 정상 완료: 전체 왕복 시나리오, 발행된 명령에 대해 테스트가 진짜 Kafka로 응답을 흘려보내면 최종 CANCELLED까지 간다")
    void fullCancellationHappyPathThroughRealKafka() {
        Reservation reservation = confirmedReservation("user1", "show-1", 1L, LocalDateTime.now().plusDays(15));

        cancelSagaOrchestrator.startCancellation(reservation.getId(), "user1");

        awaitCondition().untilAsserted(() -> {
            Reservation result = reservationRepository.findById(reservation.getId()).orElseThrow();
            assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCELLING);
        });

        CancelSaga saga = cancelSagaRepository.findByReservationId(reservation.getId()).orElseThrow();

        // seat-service인 척, 좌석 반환이 끝났다는 응답을 진짜 Kafka로 흘려보낸다.
        publish(EventTopic.SEAT_RELEASED, String.valueOf(reservation.getId()),
                new SeatReleasedEvent(saga.getId(), reservation.getId(), 1L), "1.0");

        awaitCondition().untilAsserted(() -> {
            CancelSaga updated = cancelSagaRepository.findById(saga.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(CancelSagaStatus.SEAT_RELEASED);
        });

        // payment-service인 척, 환불 완료 응답을 진짜 Kafka로 흘려보낸다.
        publish(EventTopic.PAYMENT_REFUNDED, String.valueOf(reservation.getId()),
                new PaymentRefundedEvent(saga.getId(), reservation.getId(), BigDecimal.valueOf(9000)), "1.0");

        awaitCondition().untilAsserted(() -> {
            Reservation finalReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
            CancelSaga finalSaga = cancelSagaRepository.findById(saga.getId()).orElseThrow();
            assertThat(finalReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(finalSaga.getStatus()).isEqualTo(CancelSagaStatus.COMPLETED);
        });
    }

    @Test
    @DisplayName("보상 흐름: 환불 실패 응답을 진짜 Kafka로 흘려보내면, 좌석 재점유 보상 명령이 진짜 Kafka로 나가고, 그 응답까지 받으면 CONFIRMED로 복구된다")
    void compensationFlowThroughRealKafka() {
        Reservation reservation = confirmedReservation("user1", "show-1", 1L, LocalDateTime.now().plusDays(15));
        cancelSagaOrchestrator.startCancellation(reservation.getId(), "user1");

        CancelSaga saga = cancelSagaRepository.findByReservationId(reservation.getId()).orElseThrow();
        publish(EventTopic.SEAT_RELEASED, String.valueOf(reservation.getId()),
                new SeatReleasedEvent(saga.getId(), reservation.getId(), 1L), "1.0");

        awaitCondition().untilAsserted(() ->
                assertThat(cancelSagaRepository.findById(saga.getId()).orElseThrow().getStatus())
                        .isEqualTo(CancelSagaStatus.SEAT_RELEASED));

        // payment-service인 척, 환불 실패 응답을 흘려보낸다.
        publish(EventTopic.PAYMENT_REFUND_FAILED, String.valueOf(reservation.getId()),
                new PaymentRefundFailedEvent(saga.getId(), reservation.getId(), "카드사 응답 없음"), "1.0");

        awaitCondition().untilAsserted(() ->
                assertThat(cancelSagaRepository.findById(saga.getId()).orElseThrow().getStatus())
                        .isEqualTo(CancelSagaStatus.COMPENSATING));

        // 이 시점에 SeatReserveCompensationCommand가 진짜 Kafka로 나갔어야 한다.
        rawConsumer.subscribe(List.of(EventTopic.SEAT_RESERVE_COMPENSATION_COMMAND));
        awaitCondition().untilAsserted(() -> {
            ConsumerRecords<String, String> records = rawConsumer.poll(Duration.ofMillis(500));
            boolean found = false;
            for (ConsumerRecord<String, String> record : records) {
                if (record.value().contains("\"reservationId\":" + reservation.getId())) {
                    found = true;
                }
            }
            assertThat(found).isTrue();
        });

        // seat-service인 척, 재점유(보상) 완료 응답을 흘려보낸다.
        publish(EventTopic.SEAT_RESERVED_COMPENSATED, String.valueOf(reservation.getId()),
                new SeatReservedCompensatedEvent(saga.getId(), reservation.getId(), 1L), "1.0");

        awaitCondition().untilAsserted(() -> {
            Reservation finalReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
            CancelSaga finalSaga = cancelSagaRepository.findById(saga.getId()).orElseThrow();
            assertThat(finalReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);   // 원상복구
            assertThat(finalSaga.getStatus()).isEqualTo(CancelSagaStatus.FAILED);
        });
    }
}