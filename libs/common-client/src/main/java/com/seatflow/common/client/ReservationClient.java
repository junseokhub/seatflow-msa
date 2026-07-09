package com.seatflow.common.client;

import com.seatflow.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

/**
 * fallback을 지정하면, 서킷이 열려있거나(연속 실패) 타임아웃이 나면 실제 호출 대신
 * fallback 구현이 즉시 실행된다. 호출한 쪽 스레드가 응답 없는 reservation-service를
 * 무한정 기다리지 않게 하는 게 핵심이다.
 */
@FeignClient(name = "reservation-service", url = "${seatflow.reservation.url}",
        fallback = ReservationClient.ReservationClientFallback.class)
public interface ReservationClient {

    @GetMapping("/api/reservations/{id}")
    ApiResponse<ReservationView> getReservation(@PathVariable("id") Long id);

    record ReservationView(Long id, String userId, String status, BigDecimal amount) {}

    /**
     * 서킷 오픈/타임아웃 시 실행되는 대체 동작. reservation을 확인할 수 없으니
     * 결제를 진행하면 안 된다 — 성공한 것처럼 가짜 응답을 주지 않고, 명시적으로
     * "서비스 불가" 상태를 나타내는 응답을 반환해 호출부가 즉시 실패 처리하게 한다.
     */
    class ReservationClientFallback implements ReservationClient {
        @Override
        public ApiResponse<ReservationView> getReservation(Long id) {
            return ApiResponse.fail("예매 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
    }
}