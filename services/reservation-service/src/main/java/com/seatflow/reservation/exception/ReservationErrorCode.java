package com.seatflow.reservation.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode {

    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
    RESERVATION_ALREADY_CANCELLED(HttpStatus.BAD_REQUEST, "이미 취소된 예약입니다.");

    private final HttpStatus status;
    private final String message;
}