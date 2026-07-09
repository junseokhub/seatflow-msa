package com.seatflow.coupon.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CouponErrorCode {
    CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 캠페인입니다."),
    CAMPAIGN_EXPIRED(HttpStatus.BAD_REQUEST, "종료된 캠페인입니다."),
    CAMPAIGN_SOLD_OUT(HttpStatus.CONFLICT, "쿠폰이 모두 소진되었습니다."),
    ALREADY_ISSUED(HttpStatus.CONFLICT, "이미 발급받은 쿠폰입니다."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 쿠폰입니다."),
    COUPON_NOT_OWNED(HttpStatus.FORBIDDEN, "본인 쿠폰이 아닙니다."),
    COUPON_NOT_USABLE(HttpStatus.BAD_REQUEST, "사용할 수 없는 쿠폰 상태입니다.");

    private final HttpStatus status;
    private final String message;
}