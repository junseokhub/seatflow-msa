package com.seatflow.payment.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode {

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "이미 완료된 결제입니다."),
    PAYMENT_ALREADY_FAILED(HttpStatus.BAD_REQUEST, "이미 실패한 결제입니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예매 정보를 찾을 수 없습니다."),
    RESERVATION_NOT_PAYABLE(HttpStatus.BAD_REQUEST, "결제할 수 없는 예매 상태입니다."),
    AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 예매 금액과 일치하지 않습니다."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "이미 처리 중이거나 처리된 결제 요청입니다."),
    PAYMENT_NOT_OWNED(HttpStatus.FORBIDDEN, "본인 결제가 아닙니다."),
    COUPON_INVALID(HttpStatus.BAD_REQUEST, "사용할 수 없는 쿠폰입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "결제 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}