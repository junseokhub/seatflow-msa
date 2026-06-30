package com.seatflow.payment.client;

import com.seatflow.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * reservation-service 동기 호출 클라이언트.
 * 결제 직전에 예매의 실제 금액·상태를 조회해 검증한다. 결제 금액의 진실 공급원은
 * reservation이며, 클라이언트가 보낸 금액은 이 값과 대조해 조작을 차단한다.
 * 결제는 그 순간 정확한 금액이 필요하므로(할인 만료·쿠폰 취소 등 반영) 동기 호출로 둔다.
 * 결제 직전 예매의 금액·상태를 조회해 검증한다. 공통 ApiResponse로 응답을 받는다
 * (ApiResponse는 @JsonCreator로 역직렬화를 지원한다).
 */
@FeignClient(name = "reservation-service", url = "${seatflow.reservation.url}")
public interface ReservationClient {

    @GetMapping("/api/reservations/{id}")
    ApiResponse<ReservationView> getReservation(@PathVariable("id") Long id);
}