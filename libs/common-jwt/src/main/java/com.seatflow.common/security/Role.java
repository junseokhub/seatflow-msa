package com.seatflow.common.security;

/**
 * 사용자 권한. auth가 JWT에 담아 발급하고, 각 서비스(JwtAuthenticationFilter)가
 * 검증 시 읽는다. 발급자와 검증자가 같은 enum을 공유해 문자열 오타로 어긋나지 않게
 * common-jwt에 둔다.
 */
public enum Role {
    USER,
    ADMIN
}