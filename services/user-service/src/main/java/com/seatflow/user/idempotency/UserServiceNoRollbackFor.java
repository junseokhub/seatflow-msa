package com.seatflow.user.service;

import com.seatflow.user.domain.User;
import com.seatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [4] noRollbackFor + catch — 비교 학습용, 실제로 빈 등록되지 않음.
 *
 * 처음엔 운영 코드(UserService)가 이 방식을 썼었다. 동시성 통합 테스트로
 * 실제 버그를 발견하고 [2](INSERT IGNORE)로 교체했다 — 이 클래스는 "왜
 * 문제였는지"를 직접 재현/증명하기 위해 비교용으로 남겨둔다.
 *
 * 문제: saveAndFlush()가 unique 제약 위반으로 실패하는 순간, catch로 예외를
 * 잡기 전에 이미 Spring 트랜잭션이 "rollback-only"로 마킹된다. catch해서
 * 정상 종료된 것처럼 보여도, 메서드가 끝나고 커밋을 시도하는 순간
 * UnexpectedRollbackException이 터진다. noRollbackFor는 "예외가 메서드
 * 밖으로 나갈 때"를 위한 옵션이라, 메서드 안에서 catch로 삼키는 이 구조에는
 * 애초에 적용되지 않는다.
 *
 * IdempotencyStrategyComparisonIntegrationTest에 이 클래스로 진짜 동시
 * 요청을 재현하는 테스트를 추가해뒀다 — Mock 단위 테스트로는 이 문제가
 * 절대 안 잡히고, 진짜 Spring 트랜잭션 매니저 + 진짜 DB가 있어야만
 * 드러난다는 걸 실제로 보여준다.
 */
@Slf4j
@Service("userServiceNoRollbackFor")
@RequiredArgsConstructor
public class UserServiceNoRollbackFor {

    private final UserRepository userRepository;

    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public void createUser(String userId, String email, String name) {
        try {
            User user = User.builder().id(userId).email(email).name(name).build();
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            Throwable cause = e.getRootCause();
            if (cause instanceof java.sql.SQLException sqlEx && sqlEx.getErrorCode() == 1062) {
                log.info("중복된 가입 요청 감지 - 멱등성 통과 처리(noRollbackFor): userId={}, email={}", userId, email);
                return;
            }
            throw e;
        }
    }
}