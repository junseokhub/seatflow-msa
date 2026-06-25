package com.seatflow.user.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.user.domain.User;
import com.seatflow.user.exception.UserErrorCode;
import com.seatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public void createUser(String userId, String email, String name) {
        userRepository.insertIgnore(userId, email, name, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public User getUser(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        UserErrorCode.USER_NOT_FOUND.getStatus().value(),
                        UserErrorCode.USER_NOT_FOUND.getMessage()
                ));
    }
}
