package com.seatflow.user.service;

import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.user.UserCreatedEvent;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.user.domain.User;
import com.seatflow.user.exception.UserErrorCode;
import com.seatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public User createUser(String email, String name, String phone) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(
                    UserErrorCode.EMAIL_ALREADY_EXISTS.getStatus().value(),
                    UserErrorCode.EMAIL_ALREADY_EXISTS.getMessage()
            );
        }

        User user = User.builder()
                .email(email)
                .name(name)
                .phone(phone)
                .build();

        User savedUser = userRepository.save(user);

        kafkaTemplate.send(
                EventTopic.USER_CREATED,
                String.valueOf(savedUser.getId()),
                EventEnvelope.of(
                        EventTopic.USER_CREATED,
                        "user-service",
                        new UserCreatedEvent(
                                savedUser.getId(),
                                savedUser.getEmail(),
                                savedUser.getName()
                        )
                )
        );

        return savedUser;
    }

    @Transactional(readOnly = true)
    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        UserErrorCode.USER_NOT_FOUND.getStatus().value(),
                        UserErrorCode.USER_NOT_FOUND.getMessage()
                ));
    }
}
