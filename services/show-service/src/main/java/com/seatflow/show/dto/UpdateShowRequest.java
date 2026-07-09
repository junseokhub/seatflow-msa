package com.seatflow.show.dto;

import java.time.LocalDateTime;

/**
 * 공연 수정 요청. 모든 필드는 선택적 — null이면 기존 값을 유지한다(PATCH 의미).
 * seatGrades는 변경 불가: 좌석이 이미 생성된 이후라면 등급 수정은 seat-service까지
 * 파급되므로 현재 범위에서 제외한다.
 */
public record UpdateShowRequest(
        String title,
        String venue,
        LocalDateTime showDate
) {}
