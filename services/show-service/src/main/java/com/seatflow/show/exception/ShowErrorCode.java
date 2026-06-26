package com.seatflow.show.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ShowErrorCode {
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error"),
    SHOW_NOT_FOUND(HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다.");


    private final HttpStatus status;
    private final String message;
}