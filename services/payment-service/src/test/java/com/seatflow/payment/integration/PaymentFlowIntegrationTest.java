package com.seatflow.payment.integration;

import com.seatflow.common.client.CouponClient;
import com.seatflow.common.client.ReservationClient;
import com.seatflow.common.response.ApiResponse;
import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.common.test.composition.RedisContainerSupport;
import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.domain.PaymentMethod;
import com.seatflow.payment.domain.PaymentStatus;
import com.seatflow.payment.repository.PaymentRepository;
import com.seatflow.payment.service.PaymentFacade;
import com.seatflow.payment.service.PaymentService;
import com.seatflow.payment.service.command.ProcessPaymentCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 결제 전체 흐름(PaymentFacade -> DefaultPaymentService -> Repository/Redis)을 진짜 MySQL+Redis 위에서 검증한다.
 * ReservationClient/CouponClient는 다른 서비스에 대한 외부 호출(Feign)이라 Mock으로 대체한다.
 * 진짜 서비스 간 연동까지 띄우는 end-to-end 검증은 Saga 통합 테스트 범위
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentFlowIntegrationTest implements MysqlContainerSupport, RedisContainerSupport {

     @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        MysqlContainerSupport.registerDefaultJpaProperties(registry);
    }

    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private ReservationClient reservationClient;
    @MockitoBean
    private CouponClient couponClient;

    private ReservationClient.ReservationView pendingReservation(BigDecimal amount) {
        return new ReservationClient.ReservationView(1L, "user1", "PENDING", amount);
    }

    @Test
    @DisplayName("정상 결제 요청은 완료되고 DB에 정확히 저장된다")
    void completePaymentFlowPersistsCorrectly() {
        Long reservationId = 101L;   // 이 테스트 전용 reservationId — 다른 테스트와 격리
        given(reservationClient.getReservation(reservationId))
                .willReturn(ApiResponse.ok(pendingReservation(BigDecimal.valueOf(50000))));

        Payment result = paymentFacade.pay("integration-key-1",
                new ProcessPaymentCommand(reservationId, "user1", BigDecimal.valueOf(50000),
                        PaymentMethod.CREDIT_CARD, null));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        Payment saved = paymentRepository.findById(result.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(saved.getAmount()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 동시에 여러 번 요청해도 결제는 정확히 한 번만 처리된다")
    void concurrentRequestsWithSameIdempotencyKeyProcessOnlyOnce() throws InterruptedException {
        Long reservationId = 102L;   // 이 테스트 전용 reservationId
        given(reservationClient.getReservation(reservationId))
                .willReturn(ApiResponse.ok(pendingReservation(BigDecimal.valueOf(50000))));

        int requestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateRejectedCount = new AtomicInteger();

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    paymentFacade.pay("shared-idempotency-key",
                            new ProcessPaymentCommand(reservationId, "user1", BigDecimal.valueOf(50000),
                                    PaymentMethod.CREDIT_CARD, null));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    duplicateRejectedCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateRejectedCount.get()).isEqualTo(requestCount - 1);

        long completedCountForThisReservation = paymentRepository.findAll().stream()
                .filter(p -> p.getReservationId().equals(reservationId))
                .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                .count();
        assertThat(completedCountForThisReservation).isEqualTo(1);
    }

    @Test
    @DisplayName("환불 성공 흐름 전체 검증: 완료된 결제가 REFUNDED로 바뀌고 DB에 반영된다")
    void refundFlowPersistsCorrectly() {
        Long reservationId = 103L;   // 이 테스트 전용 reservationId
        given(reservationClient.getReservation(reservationId))
                .willReturn(ApiResponse.ok(pendingReservation(BigDecimal.valueOf(50000))));
        Payment payment = paymentFacade.pay("refund-test-key",
                new ProcessPaymentCommand(reservationId, "user1", BigDecimal.valueOf(50000),
                        PaymentMethod.CREDIT_CARD, null));

        // CREDIT_CARD 전략은 process/refund 둘 다 항상 true(Mock)이므로 실제로 성공 경로를 탄다.
        paymentService.executeRefund(999L, reservationId, BigDecimal.valueOf(45000));

        Payment refunded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refunded.getRefundedAmount()).isEqualByComparingTo("45000");
    }

    @Test
    @DisplayName("Toss 결제 건의 환불은 전략(Mock)이 항상 실패를 반환하므로 결제 상태가 COMPLETED로 유지된다")
    void tossRefundStaysCompletedBecauseStrategyAlwaysFails() {
        Long reservationId = 104L;   // 이 테스트 전용 reservationId
        given(reservationClient.getReservation(reservationId))
                .willReturn(ApiResponse.ok(pendingReservation(BigDecimal.valueOf(50000))));
        Payment payment = paymentFacade.pay("toss-refund-key",
                new ProcessPaymentCommand(reservationId, "user1", BigDecimal.valueOf(50000),
                        PaymentMethod.TOSS, null));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);   // process는 true(Mock)

        paymentService.executeRefund(999L, reservationId, BigDecimal.valueOf(50000));   // refund는 항상 false(Mock)

        Payment stillCompleted = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(stillCompleted.getStatus()).isEqualTo(PaymentStatus.COMPLETED);   // 안 바뀜
    }
}