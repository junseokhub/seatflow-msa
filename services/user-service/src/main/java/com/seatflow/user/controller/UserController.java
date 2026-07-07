package com.seatflow.user.controller;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.response.ApiResponse;
import com.seatflow.user.dto.UserResponse;
import com.seatflow.user.exception.UserErrorCode;
import com.seatflow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 본인 프로필만 조회 가능. id만 알면 남의 이름·연락처 등 개인정보를 볼 수 있는
     * 걸 막는다.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @PathVariable String id,
            Authentication authentication) {
        if (!id.equals(authentication.getName())) {
            throw new BusinessException(
                    UserErrorCode.USER_NOT_OWNED.getStatus().value(),
                    UserErrorCode.USER_NOT_OWNED.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(userService.getUser(id))));
    }
}