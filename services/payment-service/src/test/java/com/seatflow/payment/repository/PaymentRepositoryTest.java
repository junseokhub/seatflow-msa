package com.seatflow.payment.repository;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.domain.PaymentMethod;
import com.seatflow.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentRepositoryTest implements MysqlContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private PaymentRepository paymentRepository;

    /**
     * @PrePersist가 저장 시점에 status를 무조건 PENDING으로 덮어쓰므로, 빌더에서
     * status를 지정해도 진짜 저장을 거치면 의미가 없다(coupon/seat/reservation에서
     * 반복 확인한 제약). FAILED/COMPLETED 등 다른 상태로 만들려면, 저장 후 도메인
     * 메서드(complete(), fail())로 전이시키고 다시 save해야 DB에 실제로 반영된다.
     */
    private Payment payment(Long reservationId, PaymentStatus status) {
        Payment saved = paymentRepository.save(Payment.builder()
                .reservationId(reservationId).userId("user1")
                .amount(BigDecimal.valueOf(50000)).paymentMethod(PaymentMethod.CREDIT_CARD)
                .build());   // 저장 시점엔 PENDING

        if (status == PaymentStatus.COMPLETED) {
            saved.complete();
        } else if (status == PaymentStatus.FAILED) {
            saved.fail();
        }
        // PENDING이면 이미 그 상태이므로 추가 전이 불필요.

        return paymentRepository.save(saved);   // 전이된 상태를 다시 DB에 반영
    }

    @Test
    @DisplayName("findByReservationId()는 해당 예매의 결제를 정확히 찾는다")
    void findByReservationIdFindsCorrectPayment() {
        paymentRepository.save(payment(1L, PaymentStatus.COMPLETED));

        Optional<Payment> result = paymentRepository.findByReservationId(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getReservationId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("findByReservationIdAndStatus()는 상태까지 정확히 일치해야 찾는다")
    void findByReservationIdAndStatusMatchesExactStatus() {
        paymentRepository.save(payment(1L, PaymentStatus.FAILED));

        Optional<Payment> completedResult =
                paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED);
        Optional<Payment> failedResult =
                paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.FAILED);

        assertThat(completedResult).isEmpty();
        assertThat(failedResult).isPresent();
    }

    @Test
    @DisplayName("같은 예매에 결제가 여러 번(실패 후 재시도) 있어도 상태별로 정확히 구분된다")
    void distinguishesMultiplePaymentsForSameReservationByStatus() {
        paymentRepository.save(payment(1L, PaymentStatus.FAILED));   // 첫 시도 실패
        paymentRepository.save(payment(1L, PaymentStatus.COMPLETED));   // 재시도 성공

        Optional<Payment> completedResult =
                paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED);

        assertThat(completedResult).isPresent();
        assertThat(completedResult.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("각 결제는 서로 다른 paymentNumber(UUID)를 자동으로 갖는다")
    void eachPaymentHasUniquePaymentNumber() {
        Payment p1 = paymentRepository.save(payment(1L, PaymentStatus.COMPLETED));
        Payment p2 = paymentRepository.save(payment(2L, PaymentStatus.COMPLETED));

        assertThat(p1.getPaymentNumber()).isNotEqualTo(p2.getPaymentNumber());
    }

    @Test
    @DisplayName("저장 시 @PrePersist가 status를 PENDING으로 초기화한다 (빌더 status는 저장 후 덮어써짐)")
    void prePersistInitializesStatus() {
        Payment payment = Payment.builder()
                .reservationId(1L).userId("user1")
                .amount(BigDecimal.valueOf(50000)).paymentMethod(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.COMPLETED)   // 빌더에서 뭘 넣든
                .build();

        Payment saved = paymentRepository.save(payment);

        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);   // @PrePersist가 이김
    }
}