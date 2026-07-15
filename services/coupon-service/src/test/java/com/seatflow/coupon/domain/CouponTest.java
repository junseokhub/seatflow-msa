package com.seatflow.coupon.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coupon 도메인의 상태 전이 규칙을 검증한다.
 * DB/Redis 없이 순수 객체 단위로 이 로직 자체가 논리적으로 맞는지만 확인한다.
 * 동시성(여러 스레드가 동시에 이 메서드를 호출했을 때의 안전성)은 통합 테스트의 몫이다.
 */
class CouponTest {

    private Coupon newCoupon() {
        return Coupon.builder()
                .campaignId(1L)
                .userId("user1")
                .discountAmount(BigDecimal.valueOf(5000))
                .build();
    }

    @Nested
    @DisplayName("reserve()")
    class Reserve {

        @Test
        @DisplayName("ISSUED 상태의 쿠폰은 reserve로 RESERVED가 된다")
        void issuedCouponCanBeReserved() {
            Coupon coupon = newCoupon();

            coupon.reserve(100L);

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.RESERVED);
            assertThat(coupon.getReservationId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("같은 reservationId로 다시 reserve해도 멱등하게 무시된다")
        void reserveIsIdempotentForSameReservation() {
            Coupon coupon = newCoupon();
            coupon.reserve(100L);

            coupon.reserve(100L);   // 재시도(중복 이벤트 등)를 가정

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.RESERVED);
            assertThat(coupon.getReservationId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("이미 USED인 쿠폰은 reserve할 수 없다")
        void usedCouponCannotBeReserved() {
            Coupon coupon = newCoupon();
            coupon.reserve(100L);
            coupon.confirmUse();

            assertThatThrownBy(() -> coupon.reserve(200L))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("RESERVED 상태에서 다른 reservationId로 reserve하면 예외가 난다")
        void reservedCouponCannotBeReservedForDifferentReservation() {
            Coupon coupon = newCoupon();
            coupon.reserve(100L);

            assertThatThrownBy(() -> coupon.reserve(200L))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("RESTORED 상태의 쿠폰은 다시 reserve할 수 있다")
        void restoredCouponCanBeReservedAgain() {
            Coupon coupon = newCoupon();
            coupon.reserve(100L);
            coupon.restore();

            coupon.reserve(200L);

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.RESERVED);
            assertThat(coupon.getReservationId()).isEqualTo(200L);
        }
    }

    @Nested
    @DisplayName("confirmUse()")
    class ConfirmUse {

        @Test
        @DisplayName("RESERVED 상태의 쿠폰은 confirmUse로 USED가 된다")
        void reservedCouponCanBeConfirmed() {
            Coupon coupon = newCoupon();
            coupon.reserve(100L);

            coupon.confirmUse();

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(coupon.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 USED인 쿠폰에 confirmUse를 다시 호출해도 멱등하게 무시된다")
        void confirmUseIsIdempotent() {
            Coupon coupon = newCoupon();
            coupon.reserve(100L);
            coupon.confirmUse();

            coupon.confirmUse();   // 중복 이벤트를 가정

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @Test
        @DisplayName("ISSUED 상태에서 바로 confirmUse하면 예외가 난다 (reserve를 건너뛸 수 없다)")
        void issuedCouponCannotBeConfirmedDirectly() {
            Coupon coupon = newCoupon();

            assertThatThrownBy(coupon::confirmUse)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("restore()")
    class Restore {

        @Test
        @DisplayName("RESERVED 상태의 쿠폰은 restore로 RESTORED가 되고 reservationId가 지워진다")
        void reservedCouponCanBeRestored() {
            Coupon coupon = newCoupon();
            coupon.reserve(100L);

            coupon.restore();

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.RESTORED);
            assertThat(coupon.getReservationId()).isNull();
        }

        @Test
        @DisplayName("USED 상태의 쿠폰도 restore로 복원될 수 있다 (결제 완료 후 취소)")
        void usedCouponCanAlsoBeRestored() {
            Coupon coupon = newCoupon();
            coupon.reserve(100L);
            coupon.confirmUse();

            coupon.restore();

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.RESTORED);
        }

        @Test
        @DisplayName("이미 RESTORED인 쿠폰에 restore를 다시 호출해도 멱등하게 무시된다")
        void restoreIsIdempotent() {
            Coupon coupon = newCoupon();
            coupon.reserve(100L);
            coupon.restore();

            coupon.restore();   // 중복 이벤트를 가정

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.RESTORED);
        }
    }
}