package com.seatflow.seat.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SeatErrorCode {

    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "좌석을 찾을 수 없습니다."),
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 예약된 좌석입니다."),
    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "이미 선택된 좌석입니다."),
    SEAT_NOT_HELD(HttpStatus.BAD_REQUEST, "점유 중인 좌석이 아닙니다."),
    SEAT_HOLD_NOT_OWNED(HttpStatus.FORBIDDEN, "본인이 선택한 좌석이 아닙니다.");

    private final HttpStatus status;
    private final String message;
}