package com.seatflow.user.service;

import com.seatflow.user.domain.User;
import com.seatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [1] save + catch (같은 트랜잭션) — 비교 학습용, 실제로 빈 등록되지 않음.
 *
 * 위험한 지점: 이 메서드가 컨슈머의 @Transactional 메서드 "안에서" 호출되면,
 * DataIntegrityViolationException이 발생하는 순간 그 트랜잭션 전체가
 * rollback-only로 마킹된다. catch로 예외를 잡아도 이미 마킹된 트랜잭션은
 * 되돌릴 수 없어, 이후 commit 시도 시 UnexpectedRollbackException이 터진다.
 * 컨슈머 쪽에 트랜잭션이 전혀 없을 때만(그리고 앞으로도 안 생긴다는 보장이
 * 있을 때만) 안전하다 — 이 "조건부 안전성"이 정식 채택하지 않은 이유다.
 */
@Slf4j
@Service("userServiceSaveAndCatch")
@RequiredArgsConstructor
public class UserServiceSaveAndCatch {

    private final UserRepository userRepository;

    @Transactional
    public void createUser(String userId, String email, String name) {
        try {
            User user = User.builder().id(userId).email(email).name(name).build();
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            log.info("중복된 가입 요청 감지 - 멱등성 통과 처리(save+catch): userId={}, email={}", userId, email);
            // 주의: 이 catch 블록 자체는 정상 작동하지만, 호출자 트랜잭션이
            // 이미 rollback-only로 마킹된 상태라면 이 메서드가 조용히 끝나도
            // 바깥 트랜잭션의 commit이 실패한다. 이게 [1] 방식의 함정이다.
        }
    }
}