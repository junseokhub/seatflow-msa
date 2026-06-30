package com.seatflow.common.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 공통 API 응답 래퍼.
 * 직렬화(응답 생성)는 정적 팩토리(ok/fail)로 하고, 역직렬화(다른 서비스가 OpenFeign으로
 * 이 응답을 받을 때)는 @JsonCreator 생성자로 한다. 모르는 필드는 무시해
 * 응답 구조가 확장돼도 클라이언트가 깨지지 않게 한다.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;

    @JsonCreator
    public ApiResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("data") T data,
            @JsonProperty("message") String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }
}