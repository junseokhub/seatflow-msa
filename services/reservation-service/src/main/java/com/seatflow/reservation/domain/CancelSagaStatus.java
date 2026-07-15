package com.seatflow.reservation.domain;

/**
 * 취소 Saga의 진행 단계. 비동기 명령/응답 사이에 "어디까지 됐나"를 추적한다.
 * 응답이 도착하면 현재 단계를 보고 다음 명령을 내리거나 보상을 시작한다.
 *
 * 정상 경로:  STARTED ->SEAT_RELEASED ->REFUNDED ->COMPLETED
 * 보상 경로:  (환불 실패) ->COMPENSATING ->FAILED  (반환했던 좌석을 다시 점유)
 */
public enum CancelSagaStatus {
    STARTED,          // 취소 시작 (CONFIRMED ->CANCELLING)
    SEAT_RELEASED,    // 좌석 반환 완료, 환불 대기
    REFUNDED,         // 환불 완료
    COMPLETED,        // 취소 최종 완료 (CANCELLED)
    COMPENSATING,     // 환불 실패 ->좌석 재점유 보상 중
    FAILED            // 보상까지 끝, 취소 실패 (원상 복구됨)
}