package com.seatflow.user.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.user.domain.User;
import com.seatflow.user.exception.UserErrorCode;
import com.seatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 회원 생성 멱등 처리. at-least-once로 같은 user.registered가 중복 도착해도 유저는 한 번만 생성되어야 한다.
 * 실제 비교 구현(전체 4가지 방식)은 idempotency-variants 폴더에 별도로 남겨뒀다(운영 코드에 영향 없음).
 *
 * ───────────────────────────────────────────────────────────────
 * 방식 비교 (멱등성 충돌 처리)
 * 자세한 트레이드오프는 4-1번외편 블로그 참고
 *
 * [1] save + catch (같은 트랜잭션) - 컨슈머 무트랜잭션 전제에서만 안전
 * [2] INSERT IGNORE (native)       <- 현재 활성
 * [3] REQUIRES_NEW + catch          - 별도 빈 분리 필요, 커넥션 비용
 * [4] noRollbackFor + catch         - 채택했다가 철회함(아래 참고)
 * [Inbox] processed_event 별도 테이블 - User는 자체 unique 제약으로 충분해 비채택
 *
 * [4]를 왜 철회했는가:
 *   처음엔 noRollbackFor + saveAndFlush + catch 조합을 채택했었다. 근데 saveAndFlush()가 unique 제약 위반으로 실패하는 순간,
 *   catch로 예외를 잡기 전에 이미 Spring 트랜잭션이 rollback-only로 마킹된다는 걸 동시성 통합 테스트에서 실제로 확인했다.
 *   catch해서 정상 종료된 것처럼 보여도, 메서드가 끝나고 커밋을 시도하는 순간 UnexpectedRollbackException이 터졌다.
 *   noRollbackFor는 예외가 메서드 밖으로 나갈 때를 위한 옵션이라, 메서드 안에서 catch로 삼키는 구조에는 애초에 적용되지 않는다.
 *
 *   Mock 기반 단위 테스트로는 이 문제가 절대 안 잡힌다.
 *   진짜 Spring 트랜잭션 매니저와 진짜 DB가 있어야 재현되는 문제였다.
 *
 * [2]를 최종 채택한 이유:
 *   예외 자체가 나지 않아 트랜잭션 오염 문제가 원천적으로 생기지 않는다.
 *   User는 필드가 단순(@PrePersist가 시각/기본값만 채움)해서 native 쿼리로 직접 값을 넣는 비용이 작다.
 *   REQUIRES_NEW([3])처럼 별도 빈 분리나 추가 커넥션 비용도 없다.
 * ───────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public void createUser(String userId, String email, String name) {
        int inserted = userRepository.insertIgnore(userId, email, name, LocalDateTime.now());
        if (inserted == 0) {
            log.info("중복된 가입 요청 감지 - 멱등성 통과 처리(INSERT IGNORE): userId={}, email={}", userId, email);
        }
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