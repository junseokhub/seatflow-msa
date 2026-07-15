package com.seatflow.user.idempotency;

import com.seatflow.user.domain.User;
import com.seatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * [3] REQUIRES_NEW + catch
 * 비교 학습용, 실제로 빈 등록되지 않음.
 *
 * 이 메서드 자체가 독립된 새 트랜잭션을 열기 때문에,
 * 여기서 발생한 DataIntegrityViolationException으로 이 트랜잭션만 롤백되고 호출자(컨슈머)의 트랜잭션은 오염되지 않는다.
 * [1]의 함정을 해결한다.
 * 다만 호출마다 진짜로 새 DB 커넥션을 하나 더 열게 되므로, 대량 동시 호출 상황에서는 커넥션 풀 소비가 [4]보다 늘어난다는 비용이 있다.
 */
@Slf4j
@Service("userServiceRequiresNew")
@RequiredArgsConstructor
public class UserServiceRequiresNew {

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createUser(String userId, String email, String name) {
        try {
            User user = User.builder().id(userId).email(email).name(name).build();
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            log.info("중복된 가입 요청 감지 - 멱등성 통과 처리(REQUIRES_NEW): userId={}, email={}", userId, email);
        }
    }
}