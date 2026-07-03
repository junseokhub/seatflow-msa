package com.seatflow.reservation.domain;

public enum ReservationStatus {
    PENDING,      // 예매 생성, 결제 대기
    CONFIRMED,    // 결제 완료로 확정
    CANCELLING,   // 취소 Saga 진행 중 (좌석 반환·환불 조율 중)
    CANCELLED     // 취소 완료
}