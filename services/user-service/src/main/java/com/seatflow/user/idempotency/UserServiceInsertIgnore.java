package com.seatflow.user.idempotency;

import com.seatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * [2] INSERT IGNORE (native)
 * 비교 학습용, 실제로 빈 등록되지 않음.
 *
 * 예외가 아예 발생하지 않으므로 트랜잭션 오염 위험이 원천적으로 없다.
 * 네가지 방식 중 가장 "안전"하다.
 * 대신 @PrePersist(생성 시각, 초기 status 등)를 우회하므로 쿼리 안에 그 값들을 직접 박아야 한다. User처럼 필드가 단순한 엔티티에는 이 비용이 작지만,
 * 연관관계나 초기화 로직이 많은 엔티티일수록 native 쿼리에 다 옮겨 적는 부담이 커진다.
 */
@Slf4j
@Service("userServiceInsertIgnore")
@RequiredArgsConstructor
public class UserServiceInsertIgnore {

    private final UserRepository userRepository;

    @Transactional
    public void createUser(String userId, String email, String name) {
        int inserted = userRepository.insertIgnore(userId, email, name, LocalDateTime.now());
        if (inserted == 0) {
            log.info("중복된 가입 요청 감지 - 멱등성 통과 처리(INSERT IGNORE): userId={}, email={}", userId, email);
        }
    }
}