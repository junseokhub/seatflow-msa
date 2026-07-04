package com.seatflow.reservation.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode {

    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
    RESERVATION_ALREADY_CANCELLED(HttpStatus.BAD_REQUEST, "이미 취소된 예약입니다."),
    RESERVATION_NOT_OWNED(HttpStatus.FORBIDDEN, "본인 예매가 아닙니다."),
    CANCELLATION_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "이미 취소가 진행 중인 예매입니다."),
    CANCELLATION_DEADLINE_PASSED(HttpStatus.BAD_REQUEST, "취소 가능 기간이 지났습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "예약 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}