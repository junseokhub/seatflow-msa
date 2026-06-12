package com.seatflow.user.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.user.domain.User;
import com.seatflow.user.dto.CreateUserRequest;
import com.seatflow.user.dto.UserResponse;
import com.seatflow.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@RequestBody @Valid CreateUserRequest request) {
        User user = userService.createUser(request.email(), request.name(), request.phone());
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(userService.getUser(id))));
    }

    @GetMapping()
    public String test() {
        return "test";
    }
}
