package com.seatflow.common.security;

/**
 * 사용자 권한. auth가 JWT에 담아 발급하고, 각 서비스(JwtAuthenticationFilter)가
 * 검증 시 읽는다. 발급자와 검증자가 같은 enum을 공유해 문자열 오타로 어긋나지 않게
 * common-jwt에 둔다.
 */
public enum Role {
    USER,
    ADMIN,
    /**
     * 사용자가 아닌 서비스 자체의 신원. OAuth2 Client Credentials로 발급된 서비스 토큰에만 담기며,
     * /internal/** 같은 서비스 간 전용 API는 이 권한만 허용한다.
     */
    SERVICE
}