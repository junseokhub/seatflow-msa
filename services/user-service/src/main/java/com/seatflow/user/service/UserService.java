package com.seatflow.user.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.user.domain.User;
import com.seatflow.user.exception.UserErrorCode;
import com.seatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    /**
     * 회원 생성 — 멱등 처리. at-least-once로 같은 user.registered가 중복 도착해도
     * 유저는 한 번만 생성되어야 한다. 세 가지 방식의 트레이드오프가 있다.
     *
     * ───────────────────────────────────────────────────────────────
     * 방식 비교 (멱등성 충돌 처리)
     *
     * [1] save + catch (같은 트랜잭션)
     *     - save()가 unique 충돌 시 DataIntegrityViolationException 발생 → 컨슈머에서 catch
     *     - 장점: JPA 라이프사이클(@PrePersist, cascade) 유지, 표준 JPA
     *     - 단점: 예외가 트랜잭션을 rollback-only로 오염 → 컨슈머에 트랜잭션 없어야만 안전(조건부)
     *
     * [2] INSERT IGNORE (native)
     *     - 충돌 시 예외 없이 0 리턴 → 트랜잭션 오염 원천 차단(항상 안전)
     *     - 단점: @PrePersist/빌더/cascade 우회 → status·시각을 쿼리에 직접 박아야 함.
     *             엔티티가 복잡(연관관계 다수)할수록 native로 다 박는 비용이 폭증.
     *     - 단순 엔티티(user)에 적합.
     *
     * [3] REQUIRES_NEW + catch  ← 현재 활성
     *     - createUser를 독립 트랜잭션으로 띄움. 충돌로 롤백돼도 "이 트랜잭션만" 롤백되고
     *       바깥(컨슈머) 트랜잭션은 오염되지 않음 → 안전.
     *     - save()를 쓰므로 JPA 라이프사이클(cascade·@PrePersist) 그대로 유지.
     *     - "오염 막기"와 "엔티티 로직 유지"를 동시에. 복잡 엔티티에 특히 유리.
     *     - 비용: 매 호출 새 트랜잭션 시작(약간의 오버헤드).
     * [4] noRollbackFor + catch  ← 추천 (복잡·배치 엔티티)
     *     - 단일 트랜잭션 내에서 unique 충돌 예외만 rollback 대상에서 제외하여 오염 방지.
     *     - 장점: save() 계열을 그대로 쓰므로 JPA 라이프사이클 및 영속성 컨텍스트 기능 유지.
     *     - 비용 효율: [3]번과 달리 새 트랜잭션을 띄우지 않아 커넥션 풀 고갈(데드락) 위험이 없음.
     *     - 정합성: 메인 비즈니스 로직 실패 시 유저/좌석 저장도 다 함께 안전하게 롤백됨.
     *      ([3]번의 치명적인 단점인 '부분 커밋으로 인한 데이터 유실'을 완벽히 방어)
     *     - 대량의 벌크 인서트(seat)나 복잡한 엔티티 정합성이 중요할 때 가장 이상적.
     * ───────────────────────────────────────────────────────────────
     */

    // REQUIRES_NEW라 이 트랜잭션만 롤백되고 호출자(컨슈머) 트랜잭션은 안전.
//  [3] @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public void createUser(String userId, String email, String name) {
        try {
            User user = User.builder()
                    .id(userId)
                    .email(email)
                    .name(name)
                    .build();
            userRepository.saveAndFlush(user);   // 라이프사이클(@PrePersist 등) 정상 동작
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 내부 원인을 한 번 더 체크해서 '유니크 제약조건 충돌'일 때만 조용히 무시
            Throwable cause = e.getRootCause();
            if (cause instanceof java.sql.SQLException && ((java.sql.SQLException) cause).getErrorCode() == 1062) {
                log.info("중복된 가입 요청 감지 - 멱등성 통과 처리: userId={}, email={}", userId, email);
                return; // noRollbackFor 덕분에 정상 흐름으로 끝나고 커밋(ACK)됨
            }

            // 만약 NOT NULL 위반 등 다른 정합성 에러라면 다시 던져서 전체 롤백 유도
            throw e;
        }
    }

    /* ───────────────────────────────────────────────────────────────
     * [2] INSERT IGNORE 버전 — 단순 엔티티에 적합, 항상 안전하나 라이프사이클 우회
     *
     * @Transactional
     * public void createUser(String userId, String email, String name) {
     *     userRepository.insertIgnore(userId, email, name, LocalDateTime.now());
     *     // 0=중복 무시, 1=신규. 예외 없음 → 트랜잭션 오염 없음.
     * }
     *
     * [1] save + catch (같은 트랜잭션) 버전 — 컨슈머 무트랜잭션 전제에서만 안전
     *
     * @Transactional
     * public void createUser(String userId, String email, String name) {
     *     try {
     *         userRepository.save(User.builder().id(userId).email(email).name(name).build());
     *     } catch (DataIntegrityViolationException e) {
     *         // 충돌 무시. 단, 같은 트랜잭션이면 rollback-only 오염 위험.
     *     }
     * }
     * ─────────────────────────────────────────────────────────────── */

    @Transactional(readOnly = true)
    public User getUser(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        UserErrorCode.USER_NOT_FOUND.getStatus().value(),
                        UserErrorCode.USER_NOT_FOUND.getMessage()
                ));
    }
}